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
