//! Ratio aggregation methods

use tracing::debug;

/// Method for aggregating ratios from multiple servers
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum AggregationMethod {
    /// Weighted geometric mean (recommended for ratios)
    GeometricMean,
    /// Simple arithmetic mean
    ArithmeticMean,
    /// Median (robust to outliers)
    Median,
}

/// Aggregate ratio matrices from multiple servers
///
/// Returns an n×n matrix of aggregated log-ratios.
pub fn aggregate_ratios(
    ratios_per_server: &[Vec<Vec<f64>>],
    weights: &[f64],
    method: AggregationMethod,
) -> Vec<Vec<f64>> {
    let n = ratios_per_server[0].len();

    let mut agg_log_r = vec![vec![0.0f64; n]; n];

    for i in 0..n {
        for j in 0..n {
            if i == j {
                agg_log_r[i][j] = 0.0;
                continue;
            }

            // Collect valid (weight, log_ratio) pairs — skip 0.0/negative/non-finite as "missing"
            let valid: Vec<(f64, f64)> = ratios_per_server
                .iter()
                .zip(weights.iter())
                .filter_map(|(matrix, &w)| {
                    let r = matrix[i][j];
                    if r > 0.0 && r.is_finite() {
                        Some((w, r.ln()))
                    } else {
                        None
                    }
                })
                .collect();

            if valid.is_empty() {
                agg_log_r[i][j] = f64::NAN; // no valid observations for this pair
                continue;
            }

            let valid_w_sum: f64 = valid.iter().map(|(w, _)| w).sum();

            agg_log_r[i][j] = match method {
                AggregationMethod::GeometricMean => {
                    valid.iter().map(|(w, log_r)| w * log_r).sum::<f64>() / valid_w_sum
                }
                AggregationMethod::ArithmeticMean => {
                    valid.iter().map(|(_, log_r)| log_r).sum::<f64>() / valid.len() as f64
                }
                AggregationMethod::Median => {
                    let mut sorted: Vec<f64> = valid.iter().map(|(_, v)| *v).collect();
                    sorted.sort_by(|a, b| a.partial_cmp(b).unwrap());
                    let cnt = sorted.len();
                    if cnt & 1 == 0 {
                        (sorted[cnt / 2 - 1] + sorted[cnt / 2]) / 2.0
                    } else {
                        sorted[cnt / 2]
                    }
                }
            };
        }
    }

    debug!("Aggregated {}x{} ratio matrix using {:?}", n, n, method);
    agg_log_r
}

/// Convert aggregated log-ratios back to ratios
pub fn exp_log_ratios(log_ratios: &[Vec<f64>]) -> Vec<Vec<f64>> {
    log_ratios
        .iter()
        .map(|row| row.iter().map(|x| x.exp()).collect())
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_geometric_mean() {
        let server_a = vec![vec![1.0, 2.0], vec![0.5, 1.0]];
        let server_b = vec![vec![1.0, 4.0], vec![0.25, 1.0]];

        let weights = vec![1.0, 1.0];
        let agg = aggregate_ratios(
            &[server_a, server_b],
            &weights,
            AggregationMethod::GeometricMean,
        );

        // Geometric mean of 2 and 4 = sqrt(8) ≈ 2.83
        assert!((agg[0][1].exp() - 2.83).abs() < 0.1);
    }

    #[test]
    fn test_median() {
        let servers: Vec<Vec<Vec<f64>>> = (0..5)
            .map(|i| vec![vec![1.0, (i + 1) as f64], vec![1.0 / (i + 1) as f64, 1.0]])
            .collect();

        let weights = vec![1.0; 5];
        let agg = aggregate_ratios(&servers, &weights, AggregationMethod::Median);

        // Median of [1,2,3,4,5] = 3
        assert!((agg[0][1].exp() - 3.0).abs() < 0.1);
    }
}
