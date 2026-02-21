//! Background price recomputation using the `price-solver` crate.
//!
//! After each price submission, this module:
//! 1. Loads the most recent submission from each server
//! 2. Aggregates ratio matrices across all servers
//! 3. Solves for true prices using least-squares
//! 4. Writes results to the `true_prices` table
//! 5. Appends a snapshot to `price_history`

use anyhow::{Context, Result};
use price_solver::{compute_prices_with_config, AggregationMethod, PriceSolverConfig};
use sqlx::{PgPool, Row};
use std::collections::HashMap;
/// Anchor item name (configurable via env var `ANCHOR_ITEM`).
fn anchor_item() -> String {
    std::env::var("ANCHOR_ITEM").unwrap_or_else(|_| "dirt".to_owned())
}

/// Anchor price (configurable via env var `ANCHOR_PRICE`).
fn anchor_price() -> f64 {
    std::env::var("ANCHOR_PRICE")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(0.10)
}

/// Recompute true prices from all server submissions.
/// Called asynchronously after each price submission.
pub async fn recompute_true_prices(pool: &PgPool) -> Result<()> {
    // Load the most recent submission from each server
    let rows = sqlx::query(
        r#"
        SELECT DISTINCT ON (server_id)
            server_id,
            item_names,
            ratio_matrix_json,
            player_count
        FROM price_submissions
        ORDER BY server_id, submitted_at DESC
        "#,
    )
    .fetch_all(pool)
    .await
    .context("fetching submissions for recomputation")?;

    if rows.is_empty() {
        tracing::debug!("no submissions yet — skipping recomputation");
        return Ok(());
    }

    // Parse submissions
    struct Submission {
        item_names: Vec<String>,
        ratio_matrix: Vec<Vec<f64>>,
        player_count: i32,
    }

    let mut submissions = Vec::new();
    for row in &rows {
        let item_names: Vec<String> = row.try_get("item_names").context("reading item_names")?;
        let matrix_json: serde_json::Value =
            row.try_get("ratio_matrix_json").context("reading matrix")?;
        let player_count: i32 = row.try_get("player_count").context("reading player_count")?;

        let ratio_matrix: Vec<Vec<f64>> = serde_json::from_value(matrix_json)
            .context("parsing ratio_matrix_json")?;

        submissions.push(Submission {
            item_names,
            ratio_matrix,
            player_count,
        });
    }

    // Build unified item index
    let mut all_items: Vec<String> = Vec::new();
    for sub in &submissions {
        for name in &sub.item_names {
            if !all_items.contains(name) {
                all_items.push(name.clone());
            }
        }
    }
    all_items.sort();

    let n = all_items.len();
    let item_idx: HashMap<&str, usize> = all_items
        .iter()
        .enumerate()
        .map(|(i, s)| (s.as_str(), i))
        .collect();

    // Build full n×n ratio matrices for each server
    let mut server_matrices: Vec<Vec<Vec<f64>>> = Vec::new();
    let mut server_weights: Vec<f64> = Vec::new();

    for sub in &submissions {
        let mut matrix = vec![vec![1.0_f64; n]; n];

        for (local_i, name_i) in sub.item_names.iter().enumerate() {
            for (local_j, name_j) in sub.item_names.iter().enumerate() {
                if let (Some(&gi), Some(&gj)) = (
                    item_idx.get(name_i.as_str()),
                    item_idx.get(name_j.as_str()),
                ) {
                    let val = sub
                        .ratio_matrix
                        .get(local_i)
                        .and_then(|row| row.get(local_j))
                        .copied()
                        .unwrap_or(1.0);

                    if val.is_finite() && val > 0.0 {
                        matrix[gi][gj] = val;
                    }
                }
            }
        }

        // One-step inference for missing cross-pairs
        for i in 0..n {
            for j in 0..n {
                if matrix[i][j] == 1.0 && i != j {
                    for k in 0..n {
                        let ik = matrix[i][k];
                        let kj = matrix[k][j];
                        if ik != 1.0 && kj != 1.0 && ik.is_finite() && kj.is_finite() {
                            matrix[i][j] = ik * kj;
                            break;
                        }
                    }
                }
            }
        }

        server_matrices.push(matrix);
        server_weights.push((sub.player_count as f64).max(1.0));
    }

    // Find anchor item
    let anchor = anchor_item();
    let anchor_idx = all_items.iter().position(|s| s == &anchor).unwrap_or(0);
    let anchor_p = anchor_price();

    let config = PriceSolverConfig {
        aggregation: AggregationMethod::GeometricMean,
        ..Default::default()
    };

    let prices =
        compute_prices_with_config(&server_matrices, Some(&server_weights), anchor_idx, anchor_p, config)
            .map_err(|e| anyhow::anyhow!("solver error: {e}"))?;

    // Write results
    let num_servers = submissions.len() as i32;
    let confidence = compute_confidence(num_servers, n);

    for (i, item_name) in all_items.iter().enumerate() {
        let price = prices[i];

        sqlx::query(
            r#"
            INSERT INTO true_prices (item_name, price, confidence, server_count)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (item_name) DO UPDATE
                SET price = EXCLUDED.price,
                    confidence = EXCLUDED.confidence,
                    server_count = EXCLUDED.server_count,
                    last_updated = NOW()
            "#,
        )
        .bind(item_name)
        .bind(price)
        .bind(confidence)
        .bind(num_servers)
        .execute(pool)
        .await
        .with_context(|| format!("upserting true price for {item_name}"))?;

        sqlx::query(
            "INSERT INTO price_history (item_name, price, server_count) VALUES ($1, $2, $3)",
        )
        .bind(item_name)
        .bind(price)
        .bind(num_servers)
        .execute(pool)
        .await
        .with_context(|| format!("inserting price history for {item_name}"))?;
    }

    tracing::info!(
        items = all_items.len(),
        servers = num_servers,
        "true prices recomputed"
    );

    Ok(())
}

/// Confidence score 0–1 based on data coverage.
fn compute_confidence(num_servers: i32, num_items: usize) -> f64 {
    let server_factor = 1.0 - (-0.5 * num_servers as f64).exp();
    let item_factor = (num_items as f64 / 100.0).min(1.0);
    (server_factor * 0.7 + item_factor * 0.3).min(1.0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_confidence_single_server() {
        let c = compute_confidence(1, 10);
        assert!(c > 0.0 && c < 1.0);
    }

    #[test]
    fn test_confidence_many_servers() {
        let c = compute_confidence(10, 100);
        assert!(c > 0.8);
    }

    #[test]
    fn test_confidence_always_in_range() {
        for servers in [0i32, 1, 3, 10, 100] {
            for items in [0usize, 1, 10, 100, 1000] {
                let c = compute_confidence(servers, items);
                assert!((0.0..=1.0).contains(&c), "confidence out of range: {c}");
            }
        }
    }
}
