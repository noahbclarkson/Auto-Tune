//! Price submission and retrieval endpoints.
//!
//! POST /api/servers/:id/prices  — submit ratio matrix (authenticated)
//! GET  /api/prices/true         — get current true prices
//! GET  /api/prices/history/:item — price history for an item

use actix_web::{web, HttpMessage, HttpRequest, HttpResponse, Responder};
use chrono::{DateTime, Utc};
use sqlx::{PgPool, Row};
use uuid::Uuid;

use crate::{
    auth::AuthenticatedServer,
    models::{
        ErrorResponse, PriceHistoryPoint, PriceHistoryResponse, SubmitPricesRequest,
        SubmitPricesResponse, TruePriceEntry, TruePricesResponse,
    },
    price_computer::recompute_true_prices,
};

/// POST /api/servers/:id/prices
pub async fn submit_prices(
    pool: web::Data<PgPool>,
    path: web::Path<Uuid>,
    req: HttpRequest,
    body: web::Json<SubmitPricesRequest>,
) -> impl Responder {
    let auth = match req.extensions().get::<AuthenticatedServer>().cloned() {
        Some(a) => a,
        None => {
            return HttpResponse::Unauthorized()
                .json(ErrorResponse::new("authentication required"));
        }
    };

    let path_server_id = path.into_inner();
    if auth.server_id != path_server_id {
        return HttpResponse::Forbidden().json(ErrorResponse::new(
            "API key does not match the server ID in the path",
        ));
    }

    let n = body.item_names.len();

    if n == 0 {
        return HttpResponse::BadRequest().json(ErrorResponse::new("item_names is empty"));
    }
    if body.ratio_matrix.len() != n {
        return HttpResponse::BadRequest().json(ErrorResponse::new(
            "ratio_matrix row count must equal item_names length",
        ));
    }
    for row in &body.ratio_matrix {
        if row.len() != n {
            return HttpResponse::BadRequest()
                .json(ErrorResponse::new("ratio_matrix must be square (n×n)"));
        }
    }

    if let Err(e) = price_solver::validate_ratio_matrix(&body.ratio_matrix) {
        return HttpResponse::UnprocessableEntity()
            .json(ErrorResponse::new(format!("invalid ratio matrix: {e}")));
    }

    // Serialize matrix as JSON for storage (PostgreSQL double precision[][] is tricky via dynamic API)
    let matrix_json = serde_json::to_value(&body.ratio_matrix).unwrap_or(serde_json::Value::Null);

    let result = sqlx::query(
        r#"
        INSERT INTO price_submissions (server_id, item_names, ratio_matrix_json, player_count)
        VALUES ($1, $2, $3, $4)
        "#,
    )
    .bind(path_server_id)
    .bind(&body.item_names)
    .bind(&matrix_json)
    .bind(body.player_count)
    .execute(pool.get_ref())
    .await;

    if let Err(e) = result {
        tracing::error!(server_id = %path_server_id, "failed to store price submission: {e}");
        return HttpResponse::InternalServerError()
            .json(ErrorResponse::new("failed to store price data"));
    }

    let _ = sqlx::query("UPDATE servers SET last_seen = NOW(), player_count = $1 WHERE id = $2")
        .bind(body.player_count)
        .bind(path_server_id)
        .execute(pool.get_ref())
        .await;

    let pool_clone = pool.get_ref().clone();
    tokio::spawn(async move {
        if let Err(e) = recompute_true_prices(&pool_clone).await {
            tracing::warn!("price recomputation failed: {e}");
        }
    });

    tracing::info!(
        server_id = %path_server_id,
        items = n,
        players = body.player_count,
        "price submission accepted"
    );

    HttpResponse::Ok().json(SubmitPricesResponse {
        success: true,
        items_processed: n,
    })
}

/// GET /api/prices/true
pub async fn get_true_prices(pool: web::Data<PgPool>) -> impl Responder {
    let result = sqlx::query(
        "SELECT item_name, price, confidence, server_count, last_updated FROM true_prices ORDER BY price DESC",
    )
    .fetch_all(pool.get_ref())
    .await;

    match result {
        Ok(rows) => {
            let last_updated: Option<DateTime<Utc>> = rows
                .first()
                .and_then(|r| r.try_get::<DateTime<Utc>, _>("last_updated").ok());

            let prices: Vec<TruePriceEntry> = rows
                .into_iter()
                .map(|r| TruePriceEntry {
                    item: r.try_get::<String, _>("item_name").unwrap_or_default(),
                    price: r.try_get::<f64, _>("price").unwrap_or(0.0),
                    confidence: r.try_get::<f64, _>("confidence").unwrap_or(0.0),
                    servers: r.try_get::<i32, _>("server_count").unwrap_or(0),
                })
                .collect();

            HttpResponse::Ok().json(TruePricesResponse {
                prices,
                last_updated,
            })
        }
        Err(e) => {
            tracing::error!("DB error fetching true prices: {e}");
            HttpResponse::InternalServerError().json(ErrorResponse::new("internal server error"))
        }
    }
}

/// GET /api/prices/history/:item
pub async fn get_price_history(pool: web::Data<PgPool>, path: web::Path<String>) -> impl Responder {
    let item_name = path.into_inner();

    let result = sqlx::query(
        r#"
        SELECT price, server_count, snapshot_at
        FROM price_history
        WHERE item_name = $1
        ORDER BY snapshot_at DESC
        LIMIT 200
        "#,
    )
    .bind(&item_name)
    .fetch_all(pool.get_ref())
    .await;

    match result {
        Ok(rows) => {
            let history: Vec<PriceHistoryPoint> = rows
                .into_iter()
                .map(|r| PriceHistoryPoint {
                    price: r.try_get::<f64, _>("price").unwrap_or(0.0),
                    server_count: r.try_get::<i32, _>("server_count").unwrap_or(0),
                    timestamp: r
                        .try_get::<DateTime<Utc>, _>("snapshot_at")
                        .unwrap_or_else(|_| Utc::now()),
                })
                .collect();

            HttpResponse::Ok().json(PriceHistoryResponse {
                item: item_name,
                history,
            })
        }
        Err(e) => {
            tracing::error!("DB error fetching price history for {item_name}: {e}");
            HttpResponse::InternalServerError().json(ErrorResponse::new("internal server error"))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use actix_web::{body::to_bytes, dev::Service, http::StatusCode, test, web, App, HttpMessage};
    use sqlx::postgres::PgPoolOptions;
    use std::time::Duration;

    fn lazy_test_pool() -> PgPool {
        PgPoolOptions::new()
            .acquire_timeout(Duration::from_millis(150))
            .connect_lazy("postgres://invalid:invalid@127.0.0.1:1/invalid")
            .expect("failed to create lazy test pool")
    }

    #[actix_web::test]
    async fn submit_prices_rejects_non_square_ratio_matrix() {
        let server_id = Uuid::new_v4();
        let app = test::init_service(
            App::new()
                .app_data(web::Data::new(lazy_test_pool()))
                .wrap_fn(move |req, srv| {
                    req.extensions_mut()
                        .insert(AuthenticatedServer { server_id });
                    srv.call(req)
                })
                .route(
                    "/api/servers/{server_id}/prices",
                    web::post().to(submit_prices),
                ),
        )
        .await;

        let request = SubmitPricesRequest {
            item_names: vec!["Dirt".to_owned(), "Cobblestone".to_owned()],
            ratio_matrix: vec![vec![1.0, 0.5]],
            player_count: 12,
        };

        let req = test::TestRequest::post()
            .uri(&format!("/api/servers/{server_id}/prices"))
            .set_json(&request)
            .to_request();

        let resp = app.call(req).await.expect("request should complete");
        assert_eq!(resp.status(), StatusCode::BAD_REQUEST);

        let body = to_bytes(resp.into_body()).await.expect("read body");
        let body_text = String::from_utf8(body.to_vec()).expect("utf8 body");
        assert!(body_text.contains("ratio_matrix row count must equal item_names length"));
    }

    #[actix_web::test]
    async fn submit_prices_valid_payload_reaches_storage_layer() {
        let server_id = Uuid::new_v4();
        let app = test::init_service(
            App::new()
                .app_data(web::Data::new(lazy_test_pool()))
                .wrap_fn(move |req, srv| {
                    req.extensions_mut()
                        .insert(AuthenticatedServer { server_id });
                    srv.call(req)
                })
                .route(
                    "/api/servers/{server_id}/prices",
                    web::post().to(submit_prices),
                ),
        )
        .await;

        let request = SubmitPricesRequest {
            item_names: vec!["Dirt".to_owned(), "Cobblestone".to_owned()],
            ratio_matrix: vec![vec![1.0, 0.5], vec![2.0, 1.0]],
            player_count: 8,
        };

        let req = test::TestRequest::post()
            .uri(&format!("/api/servers/{server_id}/prices"))
            .set_json(&request)
            .to_request();

        let resp = app.call(req).await.expect("request should complete");
        assert_eq!(resp.status(), StatusCode::INTERNAL_SERVER_ERROR);

        let body = to_bytes(resp.into_body()).await.expect("read body");
        let body_text = String::from_utf8(body.to_vec()).expect("utf8 body");
        assert!(body_text.contains("failed to store price data"));
    }

    #[actix_web::test]
    async fn submit_prices_success_when_database_available() {
        let database_url = match std::env::var("TEST_DATABASE_URL") {
            Ok(v) if !v.trim().is_empty() => v,
            _ => return,
        };

        let pool = PgPoolOptions::new()
            .max_connections(1)
            .connect(&database_url)
            .await
            .expect("connect TEST_DATABASE_URL");

        crate::db::run_migrations(&pool)
            .await
            .expect("apply migrations");

        let server_id = Uuid::new_v4();
        sqlx::query(
            "INSERT INTO servers (id, name, api_key_hash, player_count) VALUES ($1, $2, $3, 0)",
        )
        .bind(server_id)
        .bind(format!("test-server-{server_id}"))
        .bind("test-hash")
        .execute(&pool)
        .await
        .expect("insert server");

        let app = test::init_service(
            App::new()
                .app_data(web::Data::new(pool.clone()))
                .wrap_fn(move |req, srv| {
                    req.extensions_mut()
                        .insert(AuthenticatedServer { server_id });
                    srv.call(req)
                })
                .route(
                    "/api/servers/{server_id}/prices",
                    web::post().to(submit_prices),
                ),
        )
        .await;

        let request = SubmitPricesRequest {
            item_names: vec!["Dirt".to_owned(), "Cobblestone".to_owned()],
            ratio_matrix: vec![vec![1.0, 0.5], vec![2.0, 1.0]],
            player_count: 21,
        };

        let req = test::TestRequest::post()
            .uri(&format!("/api/servers/{server_id}/prices"))
            .set_json(&request)
            .to_request();

        let resp = app.call(req).await.expect("request should complete");
        assert_eq!(resp.status(), StatusCode::OK);

        let body = to_bytes(resp.into_body()).await.expect("read body");
        let body_text = String::from_utf8(body.to_vec()).expect("utf8 body");
        assert!(body_text.contains("\"success\":true"));
        assert!(body_text.contains("\"items_processed\":2"));
    }
}
