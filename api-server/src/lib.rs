//! Auto-Tune price discovery API server library
//!
//! Provides REST endpoints for cross-server price discovery via ratio matrices.

pub mod auth;
pub mod db;
pub mod models;
pub mod price_computer;
pub mod rate_limit;
pub mod routes;
