//! Server registration and listing endpoints.
//!
//! POST /api/servers/register  — register a new server, receive API key
//! GET  /api/servers           — list all registered servers

use actix_web::{web, HttpResponse, Responder};
use chrono::{DateTime, Utc};
use sqlx::PgPool;
use uuid::Uuid;

use crate::{
    auth::{generate_api_key, hash_api_key},
    models::{ErrorResponse, RegisterServerRequest, RegisterServerResponse, Server},
};

/// POST /api/servers/register
pub async fn register_server(
    pool: web::Data<PgPool>,
    body: web::Json<RegisterServerRequest>,
) -> impl Responder {
    let name = body.name.trim().to_owned();

    if name.is_empty() || name.len() > 128 {
        return HttpResponse::BadRequest().json(ErrorResponse::new("name must be 1–128 chars"));
    }

    let raw_key = generate_api_key();
    let key_hash = hash_api_key(&raw_key);

    let result = sqlx::query(
        "INSERT INTO servers (name, api_key_hash) VALUES ($1, $2) RETURNING id",
    )
    .bind(&name)
    .bind(&key_hash)
    .fetch_one(pool.get_ref())
    .await;

    match result {
        Ok(row) => {
            let id: Uuid = sqlx::Row::try_get(&row, "id").unwrap_or_else(|_| Uuid::new_v4());
            tracing::info!(server_id = %id, name, "server registered");
            HttpResponse::Created().json(RegisterServerResponse {
                server_id: id,
                api_key: raw_key,
            })
        }
        Err(e) if is_unique_violation(&e) => HttpResponse::Conflict()
            .json(ErrorResponse::new("a server with that name already exists")),
        Err(e) => {
            tracing::error!("DB error registering server: {e}");
            HttpResponse::InternalServerError().json(ErrorResponse::new("internal server error"))
        }
    }
}

/// GET /api/servers
pub async fn list_servers(pool: web::Data<PgPool>) -> impl Responder {
    let result = sqlx::query(
        "SELECT id, name, player_count, created_at, last_seen FROM servers ORDER BY last_seen DESC",
    )
    .fetch_all(pool.get_ref())
    .await;

    match result {
        Ok(rows) => {
            use sqlx::Row;
            let servers: Vec<Server> = rows
                .into_iter()
                .map(|r| Server {
                    id: r.try_get("id").unwrap_or_else(|_| Uuid::new_v4()),
                    name: r.try_get::<String, _>("name").unwrap_or_default(),
                    player_count: r.try_get::<i32, _>("player_count").unwrap_or(0),
                    created_at: r
                        .try_get::<DateTime<Utc>, _>("created_at")
                        .unwrap_or_else(|_| Utc::now()),
                    last_seen: r
                        .try_get::<DateTime<Utc>, _>("last_seen")
                        .unwrap_or_else(|_| Utc::now()),
                })
                .collect();
            HttpResponse::Ok().json(servers)
        }
        Err(e) => {
            tracing::error!("DB error listing servers: {e}");
            HttpResponse::InternalServerError().json(ErrorResponse::new("internal server error"))
        }
    }
}

fn is_unique_violation(e: &sqlx::Error) -> bool {
    if let sqlx::Error::Database(db_err) = e {
        return db_err.code().map(|c| c == "23505").unwrap_or(false);
    }
    false
}
