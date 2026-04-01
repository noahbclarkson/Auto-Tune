use actix_cors::Cors;
use actix_web::{middleware::Logger, web, App, HttpResponse, HttpServer, Responder};
use anyhow::Result;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

mod auth;
mod db;
mod models;
mod price_computer;
mod rate_limit;
mod routes;

use auth::ApiKeyAuth;
use rate_limit::{RateLimitConfig, RateLimiter};
use routes::{
    auction::configure as configure_auction,
    exchange::get_exchange_rates,
    prices::{get_price_history, get_true_prices, submit_prices},
    servers::{heartbeat, list_servers, register_server},
};

async fn health() -> impl Responder {
    HttpResponse::Ok().json(serde_json::json!({
        "status": "ok",
        "version": env!("CARGO_PKG_VERSION")
    }))
}

#[actix_web::main]
async fn main() -> Result<()> {
    // Load .env file if present
    let _ = dotenvy::dotenv();

    // Initialize tracing
    tracing_subscriber::registry()
        .with(EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()))
        .with(tracing_subscriber::fmt::layer())
        .init();

    let database_url =
        std::env::var("DATABASE_URL").expect("DATABASE_URL environment variable must be set");

    let bind_addr = std::env::var("BIND_ADDR").unwrap_or_else(|_| "0.0.0.0:8080".to_owned());

    tracing::info!(bind_addr, "starting Auto-Tune price API");

    // Database setup
    let pool = db::create_pool(&database_url).await?;
    db::run_migrations(&pool).await?;

    let pool_data = web::Data::new(pool);

    // Rate limiters
    let general_limiter = web::Data::new(RateLimiter::new(RateLimitConfig::fast()));
    let submit_limiter = web::Data::new(RateLimiter::new(RateLimitConfig::submit()));

    // Max request body size: 1 MiB (price submissions can be large ratio matrices)
    let payload_config = web::PayloadConfig::new(1_048_576usize);

    // CORS allowed origins — configure via CORS_ALLOWED_ORIGINS env var (comma-separated).
    // Defaults to localhost (dev) and autotune.dev (production).
    let allowed_origins_raw = std::env::var("CORS_ALLOWED_ORIGINS")
        .unwrap_or_else(|_| "http://localhost:3000,https://autotune.dev,https://www.autotune.dev".to_owned());
    let allowed_origins: Vec<String> = allowed_origins_raw
        .split(',')
        .map(|s| s.trim().to_owned())
        .filter(|s| !s.is_empty())
        .collect();

    HttpServer::new(move || {
        // Build CORS per-worker so each worker owns its Cors instance (not Clone)
        let origins = allowed_origins.clone();
        let cors = Cors::default()
            .allowed_origin_fn(move |origin, _req_head| {
                let origin_str = origin.to_str().unwrap_or("");
                origins.iter().any(|o| {
                    if o.starts_with("http") {
                        origin_str == *o
                    } else {
                        origin_str.contains(o)
                    }
                })
            })
            .allowed_methods(vec!["GET", "POST", "PATCH", "DELETE", "OPTIONS"])
            .allowed_headers(vec![
                actix_web::http::header::AUTHORIZATION,
                actix_web::http::header::ACCEPT,
                actix_web::http::header::CONTENT_TYPE,
                actix_web::http::header::HeaderName::from_static("x-api-key"),
            ])
            .max_age(3600);

        App::new()
            .app_data(pool_data.clone())
            .app_data(general_limiter.clone())
            .app_data(submit_limiter.clone())
            .app_data(payload_config.clone())
            .wrap(cors)
            .wrap(Logger::default())
            .route("/health", web::get().to(health))
            // Public endpoints
            .route("/api/servers/register", web::post().to(register_server))
            .route("/api/servers", web::get().to(list_servers))
            .route("/api/prices/true", web::get().to(get_true_prices))
            .route(
                "/api/prices/history/{item}",
                web::get().to(get_price_history),
            )
            .route(
                "/api/servers/exchange-rates",
                web::get().to(get_exchange_rates),
            )
            // Auction house — removed 2026-03-26. Was: full in-game GUI in Java plugin.
            // These routes now return 410 Gone.
            .configure(configure_auction)
            // Authenticated endpoints
            .service(
                web::scope("/api/servers/{server_id}")
                    .wrap(ApiKeyAuth)
                    .route("/prices", web::post().to(submit_prices))
                    .route("/heartbeat", web::post().to(heartbeat)),
            )
    })
    .bind(&bind_addr)?
    .run()
    .await?;

    Ok(())
}
