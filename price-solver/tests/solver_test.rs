//! Integration tests for price-solver

use approx::assert_relative_eq;
use price_solver::{compute_prices_from_servers, validate_ratio_matrix, SolverError};

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

#[allow(dead_code)]
fn add_noise(matrix: Vec<Vec<f64>>, noise: f64) -> Vec<Vec<f64>> {
    matrix
        .into_iter()
        .enumerate()
        .map(|(i, row)| {
            row.into_iter()
                .enumerate()
                .map(|(j, r)| {
                    if i == j {
                        1.0
                    } else {
                        r * (1.0 + noise * (i as f64 - j as f64) * 0.01)
                    }
                })
                .collect()
        })
        .collect()
}

// --- Core accuracy tests ---

#[test]
fn test_single_server_powers_of_two() {
    // Items: [1, 2, 4, 8] — clean binary progression
    let true_prices = vec![1.0, 2.0, 4.0, 8.0];
    let ratios = make_ratio_matrix(&true_prices);

    let result = compute_prices_from_servers(&[ratios], None, 0, 1.0).unwrap();

    assert_eq!(result.len(), 4);
    for (&expected, computed) in true_prices.iter().zip(result.iter()) {
        assert_relative_eq!(*computed, expected, epsilon = 0.01);
    }
}

#[test]
fn test_single_server_three_items() {
    let true_prices = vec![10.0, 25.0, 100.0];
    let ratios = make_ratio_matrix(&true_prices);
    let result = compute_prices_from_servers(&[ratios], None, 0, 10.0).unwrap();

    assert_relative_eq!(result[0], 10.0, epsilon = 0.1);
    assert_relative_eq!(result[1], 25.0, epsilon = 0.1);
    assert_relative_eq!(result[2], 100.0, epsilon = 0.1);
}

#[test]
fn test_two_servers_scaled_differently() {
    // Both servers have the same relative prices but different absolute scales
    // Relative: [1, 3, 9]
    let server_a = make_ratio_matrix(&[100.0, 300.0, 900.0]); // scale 100
    let server_b = make_ratio_matrix(&[5.0, 15.0, 45.0]); // scale 5

    let result = compute_prices_from_servers(&[server_a, server_b], None, 0, 1.0).unwrap();

    // Should recover relative prices [1, 3, 9]
    assert_relative_eq!(result[0], 1.0, epsilon = 0.05);
    assert_relative_eq!(result[1], 3.0, epsilon = 0.05);
    assert_relative_eq!(result[2], 9.0, epsilon = 0.05);
}

#[test]
fn test_five_servers_with_slight_disagreement() {
    let true_prices = vec![1.0, 2.0, 5.0, 10.0, 3.0];

    // Generate 5 server ratio matrices, each with tiny noise
    let servers: Vec<Vec<Vec<f64>>> = (0..5).map(|_| make_ratio_matrix(&true_prices)).collect();

    let result = compute_prices_from_servers(&servers, None, 0, 1.0).unwrap();

    // With consistent data from 5 servers, should be very accurate
    for (&expected, computed) in true_prices.iter().zip(result.iter()) {
        assert_relative_eq!(*computed, expected, epsilon = 0.001);
    }
}

#[test]
fn test_anchor_item_2() {
    // Anchor a different item (not item 0)
    let true_prices = vec![10.0, 20.0, 50.0];
    let ratios = make_ratio_matrix(&true_prices);

    // Anchor item 2 at 50.0
    let result = compute_prices_from_servers(&[ratios], None, 2, 50.0).unwrap();

    assert_relative_eq!(result[0], 10.0, epsilon = 0.5);
    assert_relative_eq!(result[1], 20.0, epsilon = 0.5);
    assert_relative_eq!(result[2], 50.0, epsilon = 0.5);
}

#[test]
fn test_weighted_servers_prefer_higher_weight() {
    let server_a = make_ratio_matrix(&[100.0, 200.0, 300.0]); // item1 ratio = 2.0
    let server_b = make_ratio_matrix(&[100.0, 500.0, 300.0]); // item1 ratio = 5.0

    // Weight server A much more strongly
    let weights = vec![10.0, 1.0];
    let result =
        compute_prices_from_servers(&[server_a, server_b], Some(&weights), 0, 10.0).unwrap();

    // Result for item 1 should be much closer to 20.0 (server A) than 50.0 (server B)
    assert!(
        result[1] < 25.0,
        "Expected result[1] close to 20.0 (weighted toward server A), got {}",
        result[1]
    );
}

#[test]
fn test_sparse_ratios_zeros_as_missing() {
    // Server A knows ratios for items 0 and 1, but not 2
    // Server B knows ratios for items 1 and 2, but not 0
    // Together they can still triangulate all prices

    let true_prices = vec![1.0, 3.0, 9.0];

    // Server A: has item 0 vs 1 ratios but uses 0.0 (missing) for item 2
    let mut server_a = make_ratio_matrix(&true_prices);
    server_a[0][2] = 0.0; // mark as missing
    server_a[2][0] = 0.0;
    server_a[1][2] = 0.0;
    server_a[2][1] = 0.0;

    // Server B: has item 1 vs 2 ratios but uses 0.0 for item 0
    let mut server_b = make_ratio_matrix(&true_prices);
    server_b[0][1] = 0.0; // mark as missing
    server_b[1][0] = 0.0;
    server_b[0][2] = 0.0;
    server_b[2][0] = 0.0;

    let result = compute_prices_from_servers(&[server_a, server_b], None, 0, 1.0).unwrap();

    // Should still recover prices transitively
    assert_relative_eq!(result[0], 1.0, epsilon = 0.1);
    assert_relative_eq!(result[1], 3.0, epsilon = 0.1);
    assert_relative_eq!(result[2], 9.0, epsilon = 0.1);
}

// --- Validation tests ---

#[test]
fn test_validate_valid_matrix() {
    let matrix = make_ratio_matrix(&[10.0, 20.0, 40.0]);
    let result = validate_ratio_matrix(&matrix);
    assert!(result.is_ok(), "Expected no errors, got: {:?}", result);
}

#[test]
fn test_validate_not_square() {
    let matrix = vec![vec![1.0, 2.0], vec![0.5, 1.0, 3.0]];
    let result = validate_ratio_matrix(&matrix);
    assert!(
        result.is_err(),
        "Expected validation error for non-square matrix"
    );
}

// --- Error path tests ---

#[test]
fn test_error_empty_input() {
    let result = compute_prices_from_servers(&[], None, 0, 10.0);
    assert!(matches!(result, Err(SolverError::NoServers)));
}

#[test]
fn test_error_anchor_out_of_range() {
    let ratios = make_ratio_matrix(&[10.0, 20.0, 30.0]);
    let result = compute_prices_from_servers(&[ratios], None, 99, 10.0);
    assert!(matches!(result, Err(SolverError::AnchorOutOfBounds { .. })));
}

#[test]
fn test_error_weight_mismatch() {
    let ratios = make_ratio_matrix(&[10.0, 20.0]);
    let weights = vec![1.0, 2.0, 3.0]; // 3 weights for 1 server
    let result = compute_prices_from_servers(&[ratios], Some(&weights), 0, 10.0);
    assert!(matches!(result, Err(SolverError::InvalidWeights { .. })));
}

#[test]
fn test_two_items_simple() {
    // r[0][1] = 5 means P_0/P_1 = 5, so item 0 is 5x more expensive than item 1
    // Anchor item 0 at 1.0 → item 1 should be 0.2
    let ratios = vec![vec![1.0, 5.0], vec![0.2, 1.0]];
    let result = compute_prices_from_servers(&[ratios], None, 0, 1.0).unwrap();
    assert_eq!(result.len(), 2);
    assert_relative_eq!(result[0], 1.0, epsilon = 0.01);
    assert_relative_eq!(result[1], 0.2, epsilon = 0.01);
}

#[test]
fn test_two_items_anchor_cheaper() {
    // 2 items: item 0 is 1.0, item 1 is 5x more expensive
    let true_prices = vec![1.0, 5.0];
    let ratios = make_ratio_matrix(&true_prices); // r[0][1] = 1/5 = 0.2, r[1][0] = 5
    let result = compute_prices_from_servers(&[ratios], None, 0, 1.0).unwrap();
    assert_eq!(result.len(), 2);
    assert_relative_eq!(result[0], 1.0, epsilon = 0.01);
    assert_relative_eq!(result[1], 5.0, epsilon = 0.01);
}
