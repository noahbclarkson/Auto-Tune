//! Database connection pool setup and helper utilities.

use anyhow::{Context, Result};
use sqlx::{postgres::PgPoolOptions, PgPool};
use std::time::Duration;

/// Create a PostgreSQL connection pool from the `DATABASE_URL` environment variable.
pub async fn create_pool(database_url: &str) -> Result<PgPool> {
    let pool = PgPoolOptions::new()
        .max_connections(10)
        .acquire_timeout(Duration::from_secs(10))
        .connect(database_url)
        .await
        .with_context(|| format!("failed to connect to database: {}", database_url))?;

    Ok(pool)
}

/// Run SQLx migrations from the `migrations/` directory.
pub async fn run_migrations(pool: &PgPool) -> Result<()> {
    sqlx::migrate!("./migrations")
        .run(pool)
        .await
        .context("failed to run database migrations")?;
    tracing::info!("database migrations applied");
    Ok(())
}
