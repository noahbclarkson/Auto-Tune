//! Auction house endpoints — DEPRECATED (2026-03-25)
//!
//! The auction house moved to the Java plugin as an in-game GUI feature.
//! All auction-related API endpoints now return 410 Gone.

use actix_web::HttpResponse;

/// Returns 410 Gone for any auction-related request.
/// The auction house is now implemented as an in-game GUI in the Auto-Tune Java plugin.
fn gone() -> HttpResponse {
    HttpResponse::Gone().json(serde_json::json!({
        "error": "auction house has moved to the in-game plugin",
        "message": "The auction house is now available as /auction in-game. API endpoints return 410 Gone.",
        "docs": "https://github.com/noahbclarkson/Auto-Tune"
    }))
}

pub fn configure(cfg: &mut actix_web::web::ServiceConfig) {
    cfg.service(
        actix_web::web::scope("/api/auction")
            .route("/status", actix_web::web::get().to(|| async { gone() })),
    );
}
