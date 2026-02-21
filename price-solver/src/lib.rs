//! # price-solver
//!
//! Least-squares price discovery from cross-server ratio matrices.
//!
//! ## Overview
//!
//! This crate computes "true" relative prices of items (e.g. Minecraft economy items)
//! from price ratio observations collected across multiple independent servers.
//!
//! The key insight: while individual server economies may be at completely different
//! scales, the **ratios** between item prices tend to be consistent. By observing
//! `r[i][j] = P_i / P_j` from many servers and solving a least-squares system in
//! log-space, we can recover true relative prices.
//!
//! ## Algorithm
//!
//! 1. Collect ratio matrices from m servers: `r[s][i][j] = P_i / P_j`
//! 2. Aggregate ratios using weighted geometric mean in log-space
//! 3. Build least-squares system: for each pair (i,j), `log(P_i) - log(P_j) ≈ log(r_ij)`
//! 4. Anchor one known item to set the absolute scale
//! 5. Solve via LU decomposition and exponentiate the result
//!
//! ## Example
//!
//! ```rust
//! use price_solver::compute_prices_from_servers;
//!
//! // Items: dirt=$0.10, stone=$0.20, iron=$0.40
//! // r[i][j] = P_i / P_j — so r[stone][dirt] = 0.20/0.10 = 2.0
//! let server_a = vec![
//!     vec![1.0, 0.5, 0.25],  // dirt row:  dirt/dirt, dirt/stone, dirt/iron
//!     vec![2.0, 1.0, 0.50],  // stone row: stone/dirt, stone/stone, stone/iron
//!     vec![4.0, 2.0, 1.00],  // iron row:  iron/dirt, iron/stone, iron/iron
//! ];
//!
//! // Server B has same relative prices at a different economy scale (10x)
//! let server_b = vec![
//!     vec![1.0, 0.5, 0.25],
//!     vec![2.0, 1.0, 0.50],
//!     vec![4.0, 2.0, 1.00],
//! ];
//!
//! // Anchor item 0 (dirt) at $0.10
//! let prices = compute_prices_from_servers(&[server_a, server_b], None, 0, 0.10).unwrap();
//! assert!((prices[1] - 0.20).abs() < 0.01); // stone = $0.20
//! assert!((prices[2] - 0.40).abs() < 0.01); // iron = $0.40
//! ```

pub mod aggregation;
pub mod solver;
pub mod validation;

pub use aggregation::{aggregate_ratios, AggregationMethod};
pub use solver::{
    compute_prices_from_servers, compute_prices_with_config, PriceSolverConfig, SolverError,
};
pub use validation::{validate_ratio_matrix, ValidationError};
