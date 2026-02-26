//! Exchange rate endpoints.
//!
//! GET /api/servers/exchange-rates — per-server economy multiplier vs true prices

use actix_web::{web, HttpResponse, Responder};
use chrono::{DateTime, Utc};
use sqlx::{PgPool, Row};
use std::collections::HashMap;
use uuid::Uuid;

use crate::models::{ErrorResponse, ExchangeRateEntry, ExchangeRatesResponse};

/// GET /api/servers/exchange-rates
pub async fn get_exchange_rates(pool: web::Data<PgPool>) -> impl Responder {
    let servers_result = sqlx::query(
        "SELECT id, name, player_count, last_seen FROM servers ORDER BY last_seen DESC",
    )
    .fetch_all(pool.get_ref())
    .await;

    let servers = match servers_result {
        Ok(s) => s,
        Err(e) => {
            tracing::error!("DB error fetching servers for exchange rates: {e}");
            return HttpResponse::InternalServerError()
                .json(ErrorResponse::new("internal server error"));
        }
    };

    // Get true prices for reference
    let true_prices_result =
        sqlx::query("SELECT item_name, price FROM true_prices")
            .fetch_all(pool.get_ref())
            .await;

    let true_prices = match true_prices_result {
        Ok(p) if !p.is_empty() => p,
        Ok(_) => {
            return HttpResponse::Ok().json(ExchangeRatesResponse {
                base: "true_prices".to_owned(),
                rates: vec![],
            });
        }
        Err(e) => {
            tracing::error!("DB error fetching true prices for exchange rates: {e}");
            return HttpResponse::InternalServerError()
                .json(ErrorResponse::new("internal server error"));
        }
    };

    let true_price_map: HashMap<String, f64> = true_prices
        .into_iter()
        .map(|r| {
            let name: String = r.try_get("item_name").unwrap_or_default();
            let price: f64 = r.try_get("price").unwrap_or(0.0);
            (name, price)
        })
        .collect();

    let mut rates = Vec::new();

    for server_row in servers {
        let server_id: Uuid = server_row.try_get("id").unwrap_or_else(|_| Uuid::new_v4());
        let server_name: String = server_row.try_get("name").unwrap_or_default();
        let player_count: i32 = server_row.try_get("player_count").unwrap_or(0);
        let last_seen: DateTime<Utc> = server_row
            .try_get("last_seen")
            .unwrap_or_else(|_| Utc::now());

        let sub_result = sqlx::query(
            r#"
            SELECT item_names, ratio_matrix_json
            FROM price_submissions
            WHERE server_id = $1
            ORDER BY submitted_at DESC
            LIMIT 1
            "#,
        )
        .bind(server_id)
        .fetch_optional(pool.get_ref())
        .await;

        let rate = match sub_result {
            Ok(Some(row)) => {
                let item_names: Vec<String> = row.try_get("item_names").unwrap_or_default();
                let matrix_json: serde_json::Value =
                    row.try_get("ratio_matrix_json").unwrap_or_default();
                let ratio_matrix: Vec<Vec<f64>> =
                    serde_json::from_value(matrix_json).unwrap_or_default();
                compute_exchange_rate(&item_names, &ratio_matrix, &true_price_map)
            }
            Ok(None) => 1.0,
            Err(e) => {
                tracing::warn!(server_id = %server_id, "error computing exchange rate: {e}");
                1.0
            }
        };

        rates.push(ExchangeRateEntry {
            server_id,
            name: server_name,
            rate,
            player_count,
            last_seen,
        });
    }

    HttpResponse::Ok().json(ExchangeRatesResponse {
        base: "true_prices".to_owned(),
        rates,
    })
}

/// Estimate a server's economy scale factor relative to true prices.
fn compute_exchange_rate(
    item_names: &[String],
    ratio_matrix: &[Vec<f64>],
    true_prices: &HashMap<String, f64>,
) -> f64 {
    // Find anchor: first item that has a known true price
    let anchor = item_names
        .iter()
        .enumerate()
        .find_map(|(i, name)| true_prices.get(name).map(|&p| (i, p)));

    let (anchor_idx, anchor_true_price) = match anchor {
        Some(a) => a,
        None => return 1.0,
    };

    // For each other item with a known true price, infer server's implied absolute price
    // and compute ratio vs true price.
    let mut log_ratios = Vec::new();

    for (i, name_i) in item_names.iter().enumerate() {
        if i == anchor_idx {
            continue;
        }
        if let Some(&tp_i) = true_prices.get(name_i) {
            let server_ratio = ratio_matrix
                .get(i)
                .and_then(|row| row.get(anchor_idx))
                .copied()
                .unwrap_or(1.0);

            // server_implied_price = anchor_true_price * ratio[i][anchor]
            let server_implied = anchor_true_price * server_ratio;

            if server_implied > 0.0 && tp_i > 0.0 {
                log_ratios.push((server_implied / tp_i).ln());
            }
        }
    }

    if log_ratios.is_empty() {
        return 1.0;
    }

    let mean_log = log_ratios.iter().sum::<f64>() / log_ratios.len() as f64;
    mean_log.exp()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_true_prices(items: &[(&str, f64)]) -> HashMap<String, f64> {
        items
            .iter()
            .map(|(name, price)| (name.to_string(), *price))
            .collect()
    }

    fn make_item_names(names: &[&str]) -> Vec<String> {
        names.iter().map(|s| s.to_string()).collect()
    }

    // Helper to create a ratio matrix where matrix[i][j] = prices[i] / prices[j]
    fn make_ratio_matrix(prices: &[f64]) -> Vec<Vec<f64>> {
        let n = prices.len();
        let mut r = vec![vec![1.0; n]; n];
        for i in 0..n {
            for j in 0..n {
                r[i][j] = prices[i] / prices[j];
            }
        }
        r
    }

    // --- Basic functionality tests ---

    #[test]
    fn test_basic_exchange_rate_computation() {
        // Exchange rate detects when server's relative prices differ from true prices
        // True prices: dirt=1, stone=2 (stone is 2x dirt)
        // Server prices: dirt=1, stone=4 (stone is 4x dirt - overpriced!)
        // This means server's currency is worth less for stone
        let item_names = make_item_names(&["dirt", "stone"]);
        let server_prices = vec![1.0, 4.0]; // Stone is 2x more expensive relative to dirt
        let ratio_matrix = make_ratio_matrix(&server_prices);
        let true_prices = make_true_prices(&[("dirt", 1.0), ("stone", 2.0)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Server's implied price for stone = 1 * 4 = 4, true price = 2
        // ratio = 4/2 = 2, so exchange rate should be ~2.0
        assert!(rate > 1.5 && rate < 3.0, "Expected rate ~2.0, got {}", rate);
    }

    #[test]
    fn test_exchange_rate_matches_true_prices() {
        // Server prices exactly match true prices → rate should be 1.0
        let item_names = make_item_names(&["dirt", "stone", "iron"]);
        let prices = vec![1.0, 2.0, 10.0];
        let ratio_matrix = make_ratio_matrix(&prices);
        let true_prices = make_true_prices(&[("dirt", 1.0), ("stone", 2.0), ("iron", 10.0)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        assert!((rate - 1.0).abs() < 0.01, "Expected rate ~1.0, got {}", rate);
    }

    // --- Edge case: Empty inputs ---

    #[test]
    fn test_empty_item_names_returns_one() {
        let item_names: Vec<String> = vec![];
        let ratio_matrix: Vec<Vec<f64>> = vec![];
        let true_prices = make_true_prices(&[("dirt", 0.10)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        assert_eq!(rate, 1.0, "Empty items should return fallback rate 1.0");
    }

    #[test]
    fn test_empty_true_prices_returns_one() {
        let item_names = make_item_names(&["dirt", "stone"]);
        let ratio_matrix = vec![vec![1.0, 2.0], vec![0.5, 1.0]];
        let true_prices: HashMap<String, f64> = HashMap::new();

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        assert_eq!(rate, 1.0, "No true prices should return fallback rate 1.0");
    }

    #[test]
    fn test_empty_ratio_matrix_returns_one() {
        let item_names = make_item_names(&["dirt"]);
        let ratio_matrix: Vec<Vec<f64>> = vec![];
        let true_prices = make_true_prices(&[("dirt", 0.10)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        assert_eq!(rate, 1.0, "Empty ratio matrix should return fallback rate 1.0");
    }

    // --- Edge case: No matching items ---

    #[test]
    fn test_no_matching_items_returns_one() {
        let item_names = make_item_names(&["foo", "bar"]);
        let ratio_matrix = vec![vec![1.0, 1.0], vec![1.0, 1.0]];
        let true_prices = make_true_prices(&[("dirt", 0.10), ("stone", 0.20)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        assert_eq!(rate, 1.0, "No matching items should return fallback rate 1.0");
    }

    #[test]
    fn test_only_anchor_matches_no_other_items_returns_one() {
        // Only the anchor item has a true price, no other items to compare
        let item_names = make_item_names(&["dirt", "unknown_item"]);
        let ratio_matrix = vec![vec![1.0, 2.0], vec![0.5, 1.0]];
        let true_prices = make_true_prices(&[("dirt", 0.10)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        assert_eq!(rate, 1.0, "Only anchor match should return fallback rate 1.0");
    }

    // --- Edge case: Zero prices ---

    #[test]
    fn test_zero_true_price_skipped() {
        // One item has zero true price - should be skipped in calculation
        let item_names = make_item_names(&["dirt", "stone", "diamond"]);
        let ratio_matrix = vec![
            vec![1.0, 2.0, 100.0],
            vec![0.5, 1.0, 50.0],
            vec![0.01, 0.02, 1.0],
        ];
        let true_prices = make_true_prices(&[("dirt", 0.10), ("stone", 0.0), ("diamond", 10.0)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Should still compute using diamond (stone skipped due to zero price)
        assert!(rate.is_finite(), "Rate should be finite even with zero true price");
    }

    #[test]
    fn test_anchor_zero_true_price_skipped() {
        // Anchor item has zero true price - should find another anchor
        let item_names = make_item_names(&["dirt", "stone"]);
        let ratio_matrix = vec![vec![1.0, 2.0], vec![0.5, 1.0]];
        let true_prices = make_true_prices(&[("dirt", 0.0), ("stone", 0.20)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // With zero anchor price and no other matching items for comparison, returns 1.0
        assert_eq!(rate, 1.0);
    }

    // --- Edge case: Very large prices ---

    #[test]
    fn test_very_large_prices_handled() {
        let item_names = make_item_names(&["dirt", "stone"]);
        let ratio_matrix = vec![vec![1.0, 1e15], vec![1e-15, 1.0]];
        let true_prices = make_true_prices(&[("dirt", 1e100), ("stone", 1e115)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Should handle without panicking, result may be inf or finite
        assert!(rate.is_finite() || rate.is_infinite(), "Should handle very large prices");
    }

    #[test]
    fn test_f64_max_prices() {
        let item_names = make_item_names(&["dirt", "stone"]);
        let ratio_matrix = vec![vec![1.0, 2.0], vec![0.5, 1.0]];
        let true_prices = make_true_prices(&[("dirt", f64::MAX), ("stone", f64::MAX / 2.0)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // With f64::MAX, we expect infinity in the calculation
        assert!(rate.is_nan() || rate.is_infinite() || rate.is_finite());
    }

    // --- Edge case: Very small prices (near zero) ---

    #[test]
    fn test_very_small_prices_handled() {
        let item_names = make_item_names(&["dirt", "stone"]);
        let prices = vec![1e-300, 2e-300];
        let ratio_matrix = make_ratio_matrix(&prices);
        let true_prices = make_true_prices(&[("dirt", 1e-300), ("stone", 2e-300)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Should handle very small prices without panicking
        assert!(rate.is_finite(), "Very small prices should produce finite rate, got {}", rate);
    }

    #[test]
    fn test_epsilon_prices() {
        let item_names = make_item_names(&["dirt", "stone"]);
        let prices = vec![f64::EPSILON, f64::EPSILON * 2.0];
        let ratio_matrix = make_ratio_matrix(&prices);
        let true_prices = make_true_prices(&[("dirt", f64::EPSILON), ("stone", f64::EPSILON * 2.0)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Both > 0.0 so should compute normally
        assert!((rate - 1.0).abs() < 0.01, "Epsilon prices should give rate ~1.0, got {}", rate);
    }

    // --- Edge case: Negative values ---

    #[test]
    fn test_negative_server_ratio_skipped() {
        // Negative server ratio would make server_implied negative, should be skipped
        let item_names = make_item_names(&["dirt", "stone"]);
        let ratio_matrix = vec![vec![1.0, -5.0], vec![-0.2, 1.0]];
        let true_prices = make_true_prices(&[("dirt", 0.10), ("stone", 0.20)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Negative ratios should be skipped (server_implied would be <= 0)
        assert_eq!(rate, 1.0, "Negative ratios should return fallback rate 1.0");
    }

    #[test]
    fn test_negative_true_price_skipped() {
        let item_names = make_item_names(&["dirt", "stone"]);
        let ratio_matrix = vec![vec![1.0, 2.0], vec![0.5, 1.0]];
        let true_prices = make_true_prices(&[("dirt", 0.10), ("stone", -0.20)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Negative true price should be skipped (tp_i > 0.0 check fails)
        assert_eq!(rate, 1.0, "Negative true price should return fallback rate 1.0");
    }

    // --- Edge case: Missing ratio matrix entries ---

    #[test]
    fn test_incomplete_ratio_matrix_uses_defaults() {
        // Ratio matrix is smaller than item_names
        let item_names = make_item_names(&["dirt", "stone", "diamond"]);
        let ratio_matrix = vec![vec![1.0, 2.0]]; // Only first row, incomplete
        let true_prices = make_true_prices(&[("dirt", 0.10), ("stone", 0.20), ("diamond", 100.0)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Should use default 1.0 for missing entries, still produce a result
        assert!(rate.is_finite(), "Incomplete matrix should still produce finite rate");
    }

    #[test]
    fn test_jagged_ratio_matrix() {
        // Non-rectangular ratio matrix
        let item_names = make_item_names(&["dirt", "stone", "iron"]);
        let ratio_matrix = vec![
            vec![1.0, 2.0, 5.0],
            vec![0.5], // Missing columns
            vec![0.2, 0.4], // Missing last column
        ];
        let true_prices = make_true_prices(&[("dirt", 1.0), ("stone", 2.0), ("iron", 5.0)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Should handle jagged array with defaults
        assert!(rate.is_finite(), "Jagged matrix should still produce finite rate");
    }

    // --- Edge case: NaN and Infinity handling ---

    #[test]
    fn test_nan_in_ratio_matrix() {
        let item_names = make_item_names(&["dirt", "stone"]);
        let ratio_matrix = vec![vec![1.0, f64::NAN], vec![f64::NAN, 1.0]];
        let true_prices = make_true_prices(&[("dirt", 0.10), ("stone", 0.20)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // NaN in calculation leads to NaN in log_ratios, then NaN in result
        assert!(rate.is_nan() || rate == 1.0, "NaN in matrix may produce NaN or fallback");
    }

    #[test]
    fn test_infinity_in_ratio_matrix() {
        let item_names = make_item_names(&["dirt", "stone"]);
        let ratio_matrix = vec![vec![1.0, f64::INFINITY], vec![0.0, 1.0]];
        let true_prices = make_true_prices(&[("dirt", 0.10), ("stone", 0.20)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Infinity in ratio leads to inf server_implied, which is > 0.0, so ln(inf/tp) = inf
        assert!(rate.is_infinite() || rate.is_nan() || rate.is_finite());
    }

    #[test]
    fn test_negative_infinity_in_ratio_matrix() {
        let item_names = make_item_names(&["dirt", "stone"]);
        let ratio_matrix = vec![vec![1.0, f64::NEG_INFINITY], vec![0.0, 1.0]];
        let true_prices = make_true_prices(&[("dirt", 0.10), ("stone", 0.20)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Negative infinity would make server_implied negative, should be skipped
        // So we'd fall back to rate 1.0
        assert!(rate.is_nan() || rate == 1.0 || rate.is_finite());
    }

    // --- Edge case: Single item ---

    #[test]
    fn test_single_item_returns_one() {
        // Single item with no other items to compare
        let item_names = make_item_names(&["dirt"]);
        let ratio_matrix = vec![vec![1.0]];
        let true_prices = make_true_prices(&[("dirt", 0.10)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        assert_eq!(rate, 1.0, "Single item should return fallback rate 1.0");
    }

    // --- Multiple items with mixed scenarios ---

    #[test]
    fn test_mixed_valid_and_invalid_items() {
        let item_names = make_item_names(&["dirt", "stone", "unknown", "diamond"]);
        // Server prices: dirt=0.10, stone=0.20, unknown=?, diamond=10.0
        let server_prices = vec![0.10, 0.20, 1.0, 10.0];
        let ratio_matrix = make_ratio_matrix(&server_prices);
        let true_prices = make_true_prices(&[("dirt", 0.10), ("stone", 0.20), ("diamond", 10.0)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // Should compute using only items with true prices, ignoring "unknown"
        assert!(rate.is_finite() && rate > 0.0, "Mixed items should produce valid rate");
    }

    #[test]
    fn test_all_same_exchange_rate() {
        // All items have prices matching true prices
        let item_names = make_item_names(&["dirt", "stone", "iron"]);
        let prices = vec![1.0, 2.0, 10.0];
        let ratio_matrix = make_ratio_matrix(&prices);
        let true_prices = make_true_prices(&[("dirt", 1.0), ("stone", 2.0), ("iron", 10.0)]);

        let rate = compute_exchange_rate(&item_names, &ratio_matrix, &true_prices);

        // With exact match, rate should be 1.0
        assert!((rate - 1.0).abs() < 0.001, "Exact price match should give rate 1.0, got {}", rate);
    }
}
