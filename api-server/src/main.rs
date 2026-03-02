use actix_web::{middleware::Logger, web, App, HttpResponse, HttpServer, Responder};
use anyhow::Result;
use std::sync::Arc;
use tokio::sync::RwLock;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

mod auth;
mod db;
mod matching;
mod models;
mod price_computer;
mod routes;

use auth::ApiKeyAuth;
use matching::MatchingEngine;
use routes::{
    exchange::get_exchange_rates,
    orders::{cancel_order, get_orderbook, list_orders, place_order},
    prices::{get_price_history, get_true_prices, submit_prices},
    servers::{list_servers, register_server},
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

    let database_url = std::env::var("DATABASE_URL")
        .expect("DATABASE_URL environment variable must be set");

    let bind_addr = std::env::var("BIND_ADDR").unwrap_or_else(|_| "0.0.0.0:8080".to_owned());

    tracing::info!(bind_addr, "starting Auto-Tune price API");

    // Database setup
    let pool = db::create_pool(&database_url).await?;
    db::run_migrations(&pool).await?;

    let pool_data = web::Data::new(pool);

    // Initialize matching engine for auction house
    let matching_engine = Arc::new(RwLock::new(MatchingEngine::new()));
    let engine_data = web::Data::new(matching_engine);

    HttpServer::new(move || {
        App::new()
            .app_data(pool_data.clone())
            .app_data(engine_data.clone())
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
            // Auction house endpoints
            .route("/api/orders", web::post().to(place_order))
            .route("/api/orders", web::get().to(list_orders))
            .route("/api/orders/{id}", web::delete().to(cancel_order))
            .route("/api/orderbook/{item_id}", web::get().to(get_orderbook))
            // Authenticated endpoints
            .service(
                web::scope("/api/servers/{server_id}")
                    .wrap(ApiKeyAuth)
                    .route("/prices", web::post().to(submit_prices)),
            )
    })
    .bind(&bind_addr)?
    .run()
    .await?;

    Ok(())
}
