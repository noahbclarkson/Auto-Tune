//! Core solver implementation — least-squares price discovery

use nalgebra::{DMatrix, DVector};
use thiserror::Error;
use tracing::{debug, info};

use crate::aggregation::{aggregate_ratios, AggregationMethod};

/// Errors that can occur during price solving
#[derive(Error, Debug)]
pub enum SolverError {
    #[error("No servers provided")]
    NoServers,

    #[error("Empty item list")]
    NoItems,

    #[error("Inconsistent matrix dimensions: expected {expected}x{expected}, got {rows}x{cols}")]
    InconsistentDimensions {
        expected: usize,
        rows: usize,
        cols: usize,
    },

    #[error("Invalid ratio at ({row}, {col}): {value} (must be > 0 and finite)")]
    InvalidRatio { row: usize, col: usize, value: f64 },

    #[error("Server weights must have length {expected}, got {got}")]
    InvalidWeights { expected: usize, got: usize },

    #[error("Matrix is singular — cannot solve for prices (insufficient ratio observations)")]
    SingularMatrix,

    #[error("Anchor item index {index} out of bounds (n={n})")]
    AnchorOutOfBounds { index: usize, n: usize },
}

/// Result of a price solve operation, including quality metrics.
#[derive(Debug, Clone)]
pub struct SolveResult {
    /// Computed true prices (exponentiated log-prices)
    pub prices: Vec<f64>,
    /// Quality score 0–1. Based on normalized residual error (lower = better fit).
    /// Composed with server count and item coverage factors.
    pub quality: f64,
    /// Normalized RMS residual of the LS fit. Measures how well prices explain ratios.
    pub residual_rms: f64,
    /// Number of servers that contributed to this solution
    pub num_servers: usize,
    /// Number of items in the solution
    pub num_items: usize,
}

impl SolveResult {
    /// Confidence 0–1, suitable for API responses.
    /// Primarily based on LS residual quality. Server count and item coverage
    /// provide modest bonuses when quality is already high.
    pub fn confidence(&self) -> f64 {
        let q = self.quality.clamp(0.0, 1.0);
        // Server bonus: each additional server adds ~10% of the remaining gap to 1.0
        let server_bonus =
            0.1 * (1.0 - q) * ((self.num_servers.saturating_sub(1)) as f64).min(3.0) / 3.0;
        // Item coverage bonus (plateaus at 50 items)
        let item_bonus = 0.05 * (1.0 - q) * ((self.num_items as f64 / 50.0).min(1.0));
        (q + server_bonus + item_bonus).clamp(0.0, 1.0)
    }
}

/// Configuration for the price solver
#[derive(Debug, Clone)]
pub struct PriceSolverConfig {
    /// Method for aggregating ratios across servers
    pub aggregation: AggregationMethod,
    /// Minimum number of servers required for a valid solution
    pub min_servers: usize,
    /// Anchor weight multiplier — higher = stronger anchor constraint
    pub anchor_weight: f64,
}

impl Default for PriceSolverConfig {
    fn default() -> Self {
        Self {
            aggregation: AggregationMethod::GeometricMean,
            min_servers: 1,
            anchor_weight: 1000.0,
        }
    }
}

/// Compute true prices from cross-server price ratio matrices.
///
/// Given ratio matrices r[s][i][j] = P_i / P_j observed on server s,
/// this function finds a price vector P that best explains all ratios in
/// a least-squares sense (log-space), anchored to a known reference price.
///
/// # Arguments
///
/// * `ratios_per_server` — `[m][n][n]` ratio matrices. `ratios[s][i][j] = P_i / P_j` on server s.
///   Use 0.0 or negative values to mark missing/unknown ratios (they are skipped).
/// * `server_weights` — Optional per-server trust weights. Higher weight = more influence.
/// * `anchor_item` — Index of the item whose price is known.
/// * `anchor_price` — Known absolute price for `anchor_item`.
///
/// # Returns
///
/// Vector of length n with the computed true prices.
///
/// # Example
///
/// ```
/// use price_solver::compute_prices_from_servers;
///
/// // Items with true prices [1.0, 2.0, 4.0]
/// // r[i][j] = P_i / P_j, so r[1][0] = 2.0 (item1 costs 2x item0)
/// let server_a = vec![
///     vec![1.0, 0.5, 0.25],  // item0 row: 1/1, 1/2, 1/4
///     vec![2.0, 1.0, 0.50],  // item1 row: 2/1, 2/2, 2/4
///     vec![4.0, 2.0, 1.00],  // item2 row: 4/1, 4/2, 4/4
/// ];
/// let prices = compute_prices_from_servers(&[server_a], None, 0, 1.0).unwrap();
/// assert!((prices[1] - 2.0).abs() < 0.01);
/// assert!((prices[2] - 4.0).abs() < 0.01);
/// ```
pub fn compute_prices_from_servers(
    ratios_per_server: &[Vec<Vec<f64>>],
    server_weights: Option<&[f64]>,
    anchor_item: usize,
    anchor_price: f64,
) -> Result<Vec<f64>, SolverError> {
    compute_prices_with_config(
        ratios_per_server,
        server_weights,
        anchor_item,
        anchor_price,
        PriceSolverConfig::default(),
    )
}

/// Compute prices with custom configuration.
pub fn compute_prices_with_config(
    ratios_per_server: &[Vec<Vec<f64>>],
    server_weights: Option<&[f64]>,
    anchor_item: usize,
    anchor_price: f64,
    config: PriceSolverConfig,
) -> Result<Vec<f64>, SolverError> {
    // --- Validate inputs ---
    let m = ratios_per_server.len();
    if m == 0 || m < config.min_servers {
        return Err(SolverError::NoServers);
    }

    let n = ratios_per_server[0].len();
    if n == 0 {
        return Err(SolverError::NoItems);
    }

    if anchor_item >= n {
        return Err(SolverError::AnchorOutOfBounds {
            index: anchor_item,
            n,
        });
    }

    // Validate matrix dimensions and ratio values
    for (s, matrix) in ratios_per_server.iter().enumerate() {
        if matrix.len() != n {
            return Err(SolverError::InconsistentDimensions {
                expected: n,
                rows: matrix.len(),
                cols: 0,
            });
        }
        for (i, row) in matrix.iter().enumerate() {
            if row.len() != n {
                return Err(SolverError::InconsistentDimensions {
                    expected: n,
                    rows: n,
                    cols: row.len(),
                });
            }
            for (j, &r) in row.iter().enumerate() {
                // Only validate off-diagonal entries that are meant to be real ratios
                if i != j && r > 0.0 && !r.is_finite() {
                    return Err(SolverError::InvalidRatio {
                        row: i,
                        col: j,
                        value: r,
                    });
                }
            }
        }
        let _ = s; // suppress unused warning
    }

    // Validate weights
    let weights: Vec<f64> = match server_weights {
        Some(w) => {
            if w.len() != m {
                return Err(SolverError::InvalidWeights {
                    expected: m,
                    got: w.len(),
                });
            }
            w.to_vec()
        }
        None => vec![1.0; m],
    };

    info!(
        "Solving prices: {} servers, {} items, anchor[{}]={:.4}",
        m, n, anchor_item, anchor_price
    );

    // --- Build least-squares system ---
    // For each observed pair (i, j), i < j: x_i - x_j = log(r_ij)
    // Anchor constraint: x_anchor = log(anchor_price)

    let agg_log_r = aggregate_ratios(ratios_per_server, &weights, config.aggregation);

    // Count valid edges (i < j where ratio is valid)
    let mut edges: Vec<(usize, usize, f64)> = Vec::new();
    #[allow(clippy::needless_range_loop)]
    for i in 0..n {
        for j in (i + 1)..n {
            let log_r = agg_log_r[i][j];
            if log_r.is_finite() {
                edges.push((i, j, log_r));
            }
        }
    }

    // Rows: one per edge + 1 for anchor constraint
    let num_rows = edges.len() + 1;
    let mut a_data = vec![0.0f64; num_rows * n];
    let mut b_data = vec![0.0f64; num_rows];

    for (k, &(i, j, log_r)) in edges.iter().enumerate() {
        a_data[k * n + i] = 1.0;
        a_data[k * n + j] = -1.0;
        b_data[k] = log_r;
    }

    // Anchor constraint with high weight to pin the scale
    let anchor_row = edges.len();
    let w = config.anchor_weight;
    a_data[anchor_row * n + anchor_item] = w;
    b_data[anchor_row] = anchor_price.ln() * w;

    debug!(
        "Built {}×{} least-squares system ({} edges)",
        num_rows,
        n,
        edges.len()
    );

    let a = DMatrix::from_row_slice(num_rows, n, &a_data);
    let b = DVector::from_vec(b_data);

    // Solve via normal equations: (A^T A) x = A^T b
    let ata = a.transpose() * &a;
    let atb = a.transpose() * &b;

    let x = ata.lu().solve(&atb).ok_or(SolverError::SingularMatrix)?;

    let prices: Vec<f64> = x.iter().map(|v| v.exp()).collect();

    info!("Computed prices: {:?}", prices);

    Ok(prices)
}

/// Compute prices with full quality/residual analysis.
///
/// Returns a `SolveResult` containing prices and quality metrics.
///
/// # Quality metric
///
/// The quality score is based on the normalized RMS residual of the LS fit.
/// For each edge constraint (i,j) we have `log(P_i) - log(P_j) ≈ log(r_ij)`.
/// The residual for that edge is `x_i - x_j - log(r_ij)`.
///
/// A quality of 1.0 means perfect fit (zero residuals), 0.0 means terrible fit.
pub fn compute_prices_with_quality(
    ratios_per_server: &[Vec<Vec<f64>>],
    server_weights: Option<&[f64]>,
    anchor_item: usize,
    anchor_price: f64,
    config: PriceSolverConfig,
) -> Result<SolveResult, SolverError> {
    // Build the same LS system as compute_prices_with_config
    let m = ratios_per_server.len();
    if m == 0 || m < config.min_servers {
        return Err(SolverError::NoServers);
    }
    let n = ratios_per_server[0].len();
    if n == 0 {
        return Err(SolverError::NoItems);
    }
    if anchor_item >= n {
        return Err(SolverError::AnchorOutOfBounds {
            index: anchor_item,
            n,
        });
    }

    let weights: Vec<f64> = match server_weights {
        Some(w) => {
            if w.len() != m {
                return Err(SolverError::InvalidWeights {
                    expected: m,
                    got: w.len(),
                });
            }
            w.to_vec()
        }
        None => vec![1.0; m],
    };

    let agg_log_r = aggregate_ratios(ratios_per_server, &weights, config.aggregation);

    // Collect edges
    let mut edges: Vec<(usize, usize, f64)> = Vec::new();
    #[allow(clippy::needless_range_loop)]
    for i in 0..n {
        for j in (i + 1)..n {
            let log_r = agg_log_r[i][j];
            if log_r.is_finite() {
                edges.push((i, j, log_r));
            }
        }
    }

    let num_edges = edges.len();
    let num_rows = num_edges + 1;
    let mut a_data = vec![0.0_f64; num_rows * n];
    let mut b_data = vec![0.0_f64; num_rows];

    for (k, &(i, j, log_r)) in edges.iter().enumerate() {
        a_data[k * n + i] = 1.0;
        a_data[k * n + j] = -1.0;
        b_data[k] = log_r;
    }

    let anchor_row = num_edges;
    let anchor_w = config.anchor_weight;
    a_data[anchor_row * n + anchor_item] = anchor_w;
    b_data[anchor_row] = anchor_price.ln() * anchor_w;

    let a = DMatrix::from_row_slice(num_rows, n, &a_data);
    let b = DVector::from_vec(b_data.clone());

    // Solve
    let ata = a.transpose() * &a;
    let atb = a.transpose() * &b;
    let x = ata.lu().solve(&atb).ok_or(SolverError::SingularMatrix)?;

    // Compute residual RMS on the non-anchor rows
    let mut sq_errors: Vec<f64> = Vec::new();
    for (k, &(i, j, _)) in edges.iter().enumerate() {
        let predicted = x[i] - x[j];
        let residual = predicted - b_data[k];
        sq_errors.push(residual * residual);
    }

    let residual_rms = if sq_errors.is_empty() {
        0.0
    } else {
        let mean_sq = sq_errors.iter().sum::<f64>() / sq_errors.len() as f64;
        mean_sq.sqrt()
    };

    // Normalize: a residual_rms of 0.1 means average log-ratio error of ~0.1
    // (e.g., true ratio is 2.0 but predicted log gives ~1.9)
    // Quality = exp(-5 * residual_rms) gives:
    //   rms=0.00 → quality=1.00 (perfect)
    //   rms=0.05 → quality=0.78
    //   rms=0.10 → quality=0.61
    //   rms=0.20 → quality=0.37
    //   rms=0.50 → quality=0.08
    let quality = (-5.0 * residual_rms).exp().clamp(0.0, 1.0);

    let prices: Vec<f64> = x.iter().map(|v| v.exp()).collect();

    debug!(
        "quality={:.4} residual_rms={:.6} edges={} servers={} items={}",
        quality, residual_rms, num_edges, m, n
    );

    Ok(SolveResult {
        prices,
        quality,
        residual_rms,
        num_servers: m,
        num_items: n,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use approx::assert_relative_eq;

    fn make_ratio_matrix(prices: &[f64]) -> Vec<Vec<f64>> {
        let n = prices.len();
        let mut r = vec![vec![1.0; n]; n];
        #[allow(clippy::needless_range_loop)]
        for i in 0..n {
            for j in 0..n {
                r[i][j] = prices[i] / prices[j];
            }
        }
        r
    }

    #[test]
    fn test_single_server_three_items() {
        let prices = vec![10.0, 20.0, 30.0];
        let ratios = make_ratio_matrix(&prices);
        let result = compute_prices_from_servers(&[ratios], None, 0, 10.0).unwrap();
        assert_relative_eq!(result[0], 10.0, epsilon = 0.01);
        assert_relative_eq!(result[1], 20.0, epsilon = 0.01);
        assert_relative_eq!(result[2], 30.0, epsilon = 0.01);
    }

    #[test]
    fn test_two_servers_same_ratios() {
        let server_a = make_ratio_matrix(&[100.0, 200.0, 300.0]);
        let server_b = make_ratio_matrix(&[150.0, 300.0, 450.0]); // 1.5x scaled, same ratios
        let result = compute_prices_from_servers(&[server_a, server_b], None, 0, 10.0).unwrap();
        assert_relative_eq!(result[0], 10.0, epsilon = 0.1);
        assert_relative_eq!(result[1], 20.0, epsilon = 0.1);
        assert_relative_eq!(result[2], 30.0, epsilon = 0.1);
    }

    #[test]
    fn test_weighted_servers() {
        let server_a = make_ratio_matrix(&[100.0, 200.0, 300.0]);
        let server_b = make_ratio_matrix(&[100.0, 250.0, 300.0]); // item 1 is different

        // Weight server A more heavily (0.9 vs 0.1)
        let weights = vec![0.9, 0.1];
        let result =
            compute_prices_from_servers(&[server_a, server_b], Some(&weights), 0, 10.0).unwrap();

        // Result should be much closer to server A's price (20.0) than server B's (25.0)
        assert!(
            result[1] < 21.5,
            "Expected result close to 20.0, got {}",
            result[1]
        );
    }

    #[test]
    fn test_four_items_power_of_two() {
        // prices = [1, 2, 4, 8] — clean powers of 2
        let prices = vec![1.0, 2.0, 4.0, 8.0];
        let ratios = make_ratio_matrix(&prices);
        let result = compute_prices_from_servers(&[ratios], None, 0, 1.0).unwrap();
        for (&expected, computed) in prices.iter().zip(result.iter()) {
            assert_relative_eq!(*computed, expected, epsilon = 0.01);
        }
    }

    #[test]
    fn test_error_no_servers() {
        let result = compute_prices_from_servers(&[], None, 0, 10.0);
        assert!(matches!(result, Err(SolverError::NoServers)));
    }

    #[test]
    fn test_error_anchor_out_of_bounds() {
        let ratios = make_ratio_matrix(&[10.0, 20.0]);
        let result = compute_prices_from_servers(&[ratios], None, 99, 10.0);
        assert!(matches!(result, Err(SolverError::AnchorOutOfBounds { .. })));
    }

    #[test]
    fn test_error_weight_mismatch() {
        let ratios = make_ratio_matrix(&[10.0, 20.0]);
        let weights = vec![1.0, 2.0, 3.0]; // wrong count
        let result = compute_prices_from_servers(&[ratios], Some(&weights), 0, 10.0);
        assert!(matches!(result, Err(SolverError::InvalidWeights { .. })));
    }

    #[test]
    fn test_different_anchor() {
        // Same ratios, anchor item 2 at 100.0
        let prices = vec![10.0, 20.0, 100.0];
        let ratios = make_ratio_matrix(&prices);
        let result = compute_prices_from_servers(&[ratios], None, 2, 100.0).unwrap();
        assert_relative_eq!(result[2], 100.0, epsilon = 0.1);
        // Item 0 should be 10.0, item 1 should be 20.0
        assert_relative_eq!(result[0], 10.0, epsilon = 0.5);
        assert_relative_eq!(result[1], 20.0, epsilon = 0.5);
    }

    #[test]
    fn test_quality_perfect_fit() {
        // Perfect ratios — residual should be essentially zero → quality=1.0
        let prices = vec![10.0, 20.0, 40.0];
        let ratios = make_ratio_matrix(&prices);
        let result =
            compute_prices_with_quality(&[ratios], None, 0, 10.0, Default::default()).unwrap();
        assert_relative_eq!(result.quality, 1.0, epsilon = 0.001);
        assert!(result.residual_rms < 0.001);
        assert!(result.confidence() > 0.95);
    }

    #[test]
    fn test_quality_with_noise() {
        // Server A has perfect ratios, server B has slightly noisy ratios
        let server_a = make_ratio_matrix(&[10.0, 20.0, 40.0]);
        let mut server_b = make_ratio_matrix(&[10.0, 20.0, 40.0]);
        // Add 10% noise to off-diagonal
        for (i, row) in server_b.iter_mut().enumerate().take(3) {
            for (j, cell) in row.iter_mut().enumerate().take(3) {
                if i != j {
                    *cell *= 1.10;
                }
            }
        }
        let result =
            compute_prices_with_quality(&[server_a, server_b], None, 0, 10.0, Default::default())
                .unwrap();
        // Quality should be good but not perfect
        assert!(result.quality > 0.5);
        assert!(result.quality < 1.0);
        assert!(result.residual_rms > 0.01);
    }

    #[test]
    fn test_confidence_range() {
        for items in [5, 20, 100] {
            let ratios = vec![vec![1.0_f64; items]; items];
            // Perfect diagonal matrices = zero residual → quality=1.0
            let result = compute_prices_with_quality(
                &[ratios],
                None,
                0,
                10.0,
                PriceSolverConfig {
                    min_servers: 1,
                    ..Default::default()
                },
            );
            if let Ok(r) = result {
                assert!(
                    (0.0..=1.0).contains(&r.confidence()),
                    "confidence out of range: {}",
                    r.confidence()
                );
            }
        }
    }
}
