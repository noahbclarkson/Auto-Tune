use actix_web::{web, HttpResponse};
use serde::{Deserialize, Serialize};

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(web::scope("/api/auction").route("/status", web::get().to(auction_status)));
}

async fn auction_status() -> HttpResponse {
    HttpResponse::Ok().json(serde_json::json!({
        "status": "online",
        "message": "Auction house API is running"
    }))
}
