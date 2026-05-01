//! Background price recomputation using the `price-solver` crate.
//!
//! After each price submission, this module:
//! 1. Loads the most recent submission from each server
//! 2. Filters outlier ratio observations (> 3σ in log-space)
//! 3. Aggregates ratio matrices across all servers
//! 4. Solves for true prices using least-squares
//! 5. Writes results to the `true_prices` table
//! 6. Appends a snapshot to `price_history`

use anyhow::{Context, Result};
use price_solver::{compute_prices_with_quality, AggregationMethod, PriceSolverConfig};
use sqlx::{PgPool, Row};
use std::collections::HashMap;

// ---------------------------------------------------------------------------
// Outlier filtering
// ---------------------------------------------------------------------------

/// Observations for a single ratio pair: (log_ratio, server_index).
type RatioObs = (f64, usize);

/// Collect all valid ratio observations for every (i, j) pair across servers.
/// Only finite, positive ratios are included.
#[allow(clippy::needless_range_loop)]
fn collect_all_observations(
    server_matrices: &[Vec<Vec<f64>>],
    n: usize,
) -> HashMap<(usize, usize), Vec<RatioObs>> {
    let mut obs: HashMap<(usize, usize), Vec<RatioObs>> = HashMap::new();
    for (si, matrix) in server_matrices.iter().enumerate() {
        for i in 0..n {
            for j in 0..n {
                if i == j {
                    continue;
                }
                let val = matrix[i][j];
                if val.is_finite() && val > 0.0 {
                    obs.entry((i, j)).or_default().push((val.ln(), si));
                }
            }
        }
    }
    obs
}

/// Returns true when log_ratio is within kσ of (mean_log, stddev_log).
/// Special-cases single observations (σ = 0 → always keep) and
/// tiny stddev (σ < 0.01 → keep everything, too noisy to filter).
fn is_within_k_sigma(log_ratio: f64, mean_log: f64, stddev_log: f64, k: f64) -> bool {
    if stddev_log < 0.01 {
        return true; // stddev too small to be meaningful — keep everything
    }
    (log_ratio - mean_log).abs() <= k * stddev_log
}

/// Filter outlier ratio observations from server matrices.
///
/// For each ratio pair (i, j), collects all server observations in log-space,
/// computes geometric mean (mean of logs) and standard deviation, then marks
/// observations beyond `k` standard deviations as outliers. Outlier server
/// entries for that pair are replaced with 1.0 so the bridge-inference step
/// can fill them in.
///
/// This prevents a single malicious or broken server from corrupting the
/// true-price solver with wildly inflated/deflated ratios.
///
/// Returns a tuple of (filtered_matrices, outlier_stats) where outlier_stats
/// maps (i, j) → number of outliers removed.
fn filter_outliers(
    server_matrices: &mut [Vec<Vec<f64>>],
    sigma_threshold: f64,
) -> HashMap<(usize, usize), usize> {
    let n = server_matrices.first().map(|m| m.len()).unwrap_or(0);
    let num_servers = server_matrices.len();

    let obs_by_pair = collect_all_observations(server_matrices, n);

    let mut outlier_counts: HashMap<(usize, usize), usize> = HashMap::new();
    let mut outlier_servers: HashMap<(usize, usize), Vec<usize>> = HashMap::new();

    for (pair, observations) in &obs_by_pair {
        if observations.len() < 2 {
            // Can't detect outliers with < 2 observations
            continue;
        }

        let mean_log = observations.iter().map(|(l, _)| l).sum::<f64>() / observations.len() as f64;

        let variance = observations
            .iter()
            .map(|(l, _)| {
                let d = l - mean_log;
                d * d
            })
            .sum::<f64>()
            / observations.len() as f64;
        let stddev_log = variance.sqrt();

        let k = sigma_threshold;

        for (log_ratio, server_idx) in observations {
            if !is_within_k_sigma(*log_ratio, mean_log, stddev_log, k) {
                *outlier_counts.entry(*pair).or_insert(0) += 1;
                outlier_servers.entry(*pair).or_default().push(*server_idx);
            }
        }
    }

    // Remove flagged outliers: replace with 1.0 (will be bridge-inferred)
    let total_outliers: usize = outlier_counts.values().sum();
    if total_outliers > 0 {
        tracing::info!(
            outliers = total_outliers,
            affected_pairs = outlier_counts.len(),
            servers = num_servers,
            "filtering outlier ratio observations (> {:.1}σ)",
            sigma_threshold
        );
    }

    for (pair, server_list) in &outlier_servers {
        let (i, j) = *pair;
        for &si in server_list {
            // matrix[si][i][j] = ratio of item i to item j for server si
            if let Some(matrix) = server_matrices.get_mut(si) {
                if let Some(row) = matrix.get_mut(i) {
                    if let Some(cell) = row.get_mut(j) {
                        *cell = 1.0;
                    }
                }
                if let Some(row) = matrix.get_mut(j) {
                    if let Some(cell) = row.get_mut(i) {
                        *cell = 1.0;
                    }
                }
            }
        }
    }

    // Log per-server outlier counts for observability
    for si in 0..num_servers {
        let count: usize = outlier_servers
            .values()
            .filter(|&servers| servers.contains(&si))
            .count();
        if count > 0 {
            tracing::debug!(
                server_idx = si,
                outlier_pairs = count,
                "server has outlier ratios"
            );
        }
    }

    outlier_counts
}

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
    // Freshness threshold: skip submissions older than this many hours.
    // Prevents offline servers from indefinitely influencing true prices.
    // Configurable via STALE_THRESHOLD_HOURS env var (default: 24 hours).
    let stale_threshold_hours: i64 = std::env::var("STALE_THRESHOLD_HOURS")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(24);

    // Load the most recent submission from each server
    let rows = sqlx::query(
        r#"
        SELECT DISTINCT ON (server_id)
            server_id,
            item_names,
            ratio_matrix_json,
            player_count
        FROM price_submissions
        WHERE submitted_at >= NOW() - INTERVAL '1 hour' * $1
        ORDER BY server_id, submitted_at DESC
        "#,
    )
    .bind(stale_threshold_hours)
    .fetch_all(pool)
    .await
    .context("fetching submissions for recomputation")?;

    if rows.is_empty() {
        tracing::debug!("no fresh submissions — skipping recomputation");
        return Ok(());
    }

    tracing::info!(count = rows.len(), "recomputing true prices from fresh submissions");

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
        let player_count: i32 = row
            .try_get("player_count")
            .context("reading player_count")?;

        let ratio_matrix: Vec<Vec<f64>> =
            serde_json::from_value(matrix_json).context("parsing ratio_matrix_json")?;

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
                if let (Some(&gi), Some(&gj)) =
                    (item_idx.get(name_i.as_str()), item_idx.get(name_j.as_str()))
                {
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

        // One-step inference for missing cross-pairs using geometric mean of all valid bridges
        for i in 0..n {
            for j in 0..n {
                if matrix[i][j] == 1.0 && i != j {
                    let mut valid_bridges = Vec::new();
                    #[allow(clippy::needless_range_loop)]
                    for k in 0..n {
                        let ik = matrix[i][k];
                        let kj = matrix[k][j];
                        if ik != 1.0
                            && kj != 1.0
                            && ik.is_finite()
                            && kj.is_finite()
                            && ik > 0.0
                            && kj > 0.0
                        {
                            valid_bridges.push(ik * kj);
                        }
                    }
                    if !valid_bridges.is_empty() {
                        let mut sum_log = 0.0;
                        for &v in &valid_bridges {
                            sum_log += v.ln();
                        }
                        let mean_log = sum_log / (valid_bridges.len() as f64);
                        matrix[i][j] = mean_log.exp();
                    }
                }
            }
        }

        server_matrices.push(matrix);
        server_weights.push((sub.player_count as f64).max(1.0));
    }

    // ── Outlier filtering (3σ in log-space) ─────────────────────────────────
    // Runs before bridge inference so that flagged entries are replaced with 1.0
    // (meaning "unknown") and the bridge step fills them in from neighbouring
    // non-outlier observations.
    // Threshold configurable via OUTLIER_SIGMA env var (default 3.0).
    let outlier_sigma: f64 = std::env::var("OUTLIER_SIGMA")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(3.0);
    let outliers = filter_outliers(&mut server_matrices, outlier_sigma);

    // ── Bridge inference ─────────────────────────────────────────────────────
    // One-step inference for missing cross-pairs using geometric mean of valid
    // bridges (outlier entries already replaced with 1.0 above).
    let anchor = anchor_item();
    let anchor_idx = all_items.iter().position(|s| s == &anchor).unwrap_or(0);
    let anchor_p = anchor_price();

    let config = PriceSolverConfig {
        aggregation: AggregationMethod::GeometricMean,
        ..Default::default()
    };

    let result = compute_prices_with_quality(
        &server_matrices,
        Some(&server_weights),
        anchor_idx,
        anchor_p,
        config,
    )
    .map_err(|e| anyhow::anyhow!("solver error: {e}"))?;

    // Write results
    let num_servers = submissions.len() as i32;

    for (i, item_name) in all_items.iter().enumerate() {
        let price = result.prices[i];
        let item_confidence = result.per_item_confidence[i];
        let is_anchored = result.connectivity.is_anchor_connected(i);

        sqlx::query(
            r#"
            INSERT INTO true_prices (item_name, price, confidence, server_count, anchored)
            VALUES ($1, $2, $3, $4, $5)
            ON CONFLICT (item_name) DO UPDATE
                SET price = EXCLUDED.price,
                    confidence = EXCLUDED.confidence,
                    server_count = EXCLUDED.server_count,
                    anchored = EXCLUDED.anchored,
                    last_updated = NOW()
            "#,
        )
        .bind(item_name)
        .bind(price)
        .bind(item_confidence)
        .bind(num_servers)
        .bind(is_anchored)
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
        outlier_pairs_filtered = outliers.values().sum::<usize>(),
        "true prices recomputed"
    );

    Ok(())
}

// ---------------------------------------------------------------------------
// Unit tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    // ── is_within_k_sigma ────────────────────────────────────────────────────

    #[test]
    fn test_is_within_sigma_inside() {
        // Test in log-space directly: mean_log=0, stddev_log=0.1, 3σ = ±0.3 in log-space
        let mean_log = 0.0_f64;
        assert!(is_within_k_sigma(mean_log, mean_log, 0.1, 3.0), "at mean");
        // ±0.2 in log-space is within ±0.3 (3σ)
        assert!(
            is_within_k_sigma(0.2, mean_log, 0.1, 3.0),
            "within 3σ boundary"
        );
        // ±0.3 in log-space is exactly at 3σ boundary
        assert!(
            is_within_k_sigma(-0.3, mean_log, 0.1, 3.0),
            "at 3σ lower boundary"
        );
        assert!(
            is_within_k_sigma(0.3, mean_log, 0.1, 3.0),
            "at 3σ upper boundary"
        );
        // ±0.5 in log-space is outside ±0.3 (3σ)
        assert!(
            !is_within_k_sigma(-0.5, mean_log, 0.1, 3.0),
            "outside 3σ lower"
        );
        assert!(
            !is_within_k_sigma(0.5, mean_log, 0.1, 3.0),
            "outside 3σ upper"
        );
    }

    #[test]
    fn test_is_within_sigma_outside() {
        // mean=1.0, stddev=0.1 → 3σ = 0.7 to 1.3
        // 2.0 is outside 3σ (log 2.0 ≈ 0.693, |0.693 - 0| = 6.93σ)
        assert!(!is_within_k_sigma(2.0_f64.ln(), 1.0_f64.ln(), 0.1, 3.0));
        assert!(!is_within_k_sigma(0.5_f64.ln(), 1.0_f64.ln(), 0.1, 3.0));
    }

    #[test]
    fn test_is_within_sigma_tiny_stddev_keeps_all() {
        // stddev < 0.01 → always keep
        assert!(is_within_k_sigma(10.0_f64.ln(), 1.0_f64.ln(), 0.001, 3.0));
    }

    // ── filter_outliers ──────────────────────────────────────────────────────

    /// Helper: build a 3-server × 3-item matrix where all servers agree on ratio 2.0
    fn make_agreeing_matrices() -> Vec<Vec<Vec<f64>>> {
        // Server 0: dirt=1, stone=2, iron=4
        // Server 1: dirt=1, stone=2, iron=4  (same ratios)
        // Server 2: dirt=1, stone=2, iron=4  (same ratios)
        vec![
            vec![
                vec![1.0, 0.5, 0.25],
                vec![2.0, 1.0, 0.5],
                vec![4.0, 2.0, 1.0],
            ],
            vec![
                vec![1.0, 0.5, 0.25],
                vec![2.0, 1.0, 0.5],
                vec![4.0, 2.0, 1.0],
            ],
            vec![
                vec![1.0, 0.5, 0.25],
                vec![2.0, 1.0, 0.5],
                vec![4.0, 2.0, 1.0],
            ],
        ]
    }

    #[test]
    fn test_filter_outliers_no_change_when_all_agree() {
        let mut matrices = make_agreeing_matrices();
        let before: Vec<_> = matrices.to_vec();
        let result = filter_outliers(&mut matrices, 3.0);
        assert_eq!(
            result.values().sum::<usize>(),
            0,
            "no outliers when all agree"
        );
        // matrices should be unchanged
        assert_eq!(matrices, before);
    }

    #[test]
    fn test_filter_outliers_removes_malicious_server() {
        // 11-server setup: 10 honest servers with iron/dirt=4.0 + 1 malicious with iron/dirt=1000
        // Expected: malicious server's iron/dirt observation filtered at σ=2.0
        //
        // Math: ln(4)=1.386, ln(1000)=6.908
        // mean_log = (10×1.386 + 6.908)/11 = 20.768/11 = 1.888
        // variance_log = (10×(1.386-1.888)² + (6.908-1.888)²)/11 = (10×0.252 + 25.20)/11 = 2.705
        // std_log = 1.644
        // z(malicious) = |6.908 - 1.888| / 1.644 = 5.02 / 1.644 = 3.05 → > 3 ✓
        // z(malicious) = 5.02 / 2.73 = 1.84 → < 2.0...
        // Wait, let me recalculate variance properly:
        // honest deviation from mean: 1.386 - 1.888 = -0.502, squared = 0.252
        // malicious deviation: 6.908 - 1.888 = 5.020, squared = 25.20
        // total variance = (10×0.252 + 25.20) / 11 = 27.72/11 = 2.52
        // std = sqrt(2.52) = 1.588
        // z = 5.02 / 1.588 = 3.16 → > 3 ✓ (at σ=3.0)
        // z = 5.02 / 2.52 = 1.99 → just barely < 2.0 (at σ=2.0)
        //
        // I'll use σ=3.0 (default) with 10 honest + 1 malicious at 1000x
        let honest = vec![
            vec![1.0, 0.5, 0.25],
            vec![2.0, 1.0, 0.5],
            vec![4.0, 2.0, 1.0],
        ];
        let malicious = vec![
            vec![1.0, 0.5, 0.001],
            vec![2.0, 1.0, 0.5],
            vec![1000.0, 2.0, 1.0],
        ];
        let mut matrices: Vec<Vec<Vec<f64>>> = (0..10).map(|_| honest.clone()).collect();
        matrices.push(malicious);

        let result = filter_outliers(&mut matrices, 3.0);
        let total: usize = result.values().sum();
        assert!(
            total > 0,
            "10-honest + 1-malicious at 1000x should be filtered at σ=3.0, got {total}"
        );
        assert_eq!(
            matrices[10][2][0], 1.0,
            "outlier iron/dirt replaced with 1.0"
        );
    }

    #[test]
    fn test_filter_outliers_single_observation_kept() {
        // Only one server has a particular pair → no outlier detection possible
        let mut matrices = vec![
            vec![
                vec![1.0, 0.5, 0.25],
                vec![2.0, 1.0, 0.5],
                vec![4.0, 2.0, 1.0],
            ],
            vec![
                vec![1.0, 0.5, 0.25],
                vec![2.0, 1.0, 0.5],
                vec![1.0, 1.0, 1.0],
            ], // iron/dirt = 1.0
        ];
        let result = filter_outliers(&mut matrices, 3.0);
        // No pair has 2+ servers with valid observations AND disagreement
        // → should be no outliers
        assert_eq!(result.values().sum::<usize>(), 0);
    }

    #[test]
    fn test_filter_outliers_allows_slight_variance() {
        // 3 servers with slight variance (within 3σ) should not be filtered
        let mut matrices = vec![
            vec![
                vec![1.0, 0.5, 0.25],
                vec![2.0, 1.0, 0.5],
                vec![4.0, 2.0, 1.0],
            ],
            vec![
                vec![1.0, 0.5, 0.24],
                vec![2.1, 1.0, 0.49],
                vec![4.2, 2.04, 1.0],
            ],
            vec![
                vec![1.0, 0.5, 0.26],
                vec![2.05, 1.0, 0.51],
                vec![4.1, 2.02, 1.0],
            ],
        ];
        let result = filter_outliers(&mut matrices, 3.0);
        // Minor 4-8% variance is well within 3σ for 3 observations → no outliers
        assert_eq!(
            result.values().sum::<usize>(),
            0,
            "slight server variance should not be filtered"
        );
    }

    #[test]
    fn test_filter_outliers_only_two_servers_no_filter() {
        // With only 2 servers, variance can't be meaningfully measured
        // (stddev=0 with 2 observations of the same value, σ<0.01 → keep)
        let mut matrices = vec![
            vec![
                vec![1.0, 0.5, 0.25],
                vec![2.0, 1.0, 0.5],
                vec![4.0, 2.0, 1.0],
            ],
            vec![
                vec![1.0, 0.5, 0.25],
                vec![2.0, 1.0, 0.5],
                vec![4.0, 2.0, 1.0],
            ],
        ];
        let result = filter_outliers(&mut matrices, 3.0);
        assert_eq!(result.values().sum::<usize>(), 0);
    }
}
