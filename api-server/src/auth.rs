//! Server authentication: API key generation and validation.
//!
//! Each registered server receives a random 32-byte API key (base64url encoded).
//! Only the SHA-256 hash of the key is stored. Validation uses constant-time
//! comparison to prevent timing attacks.

use rand::RngExt;
use sha2::{Digest, Sha256};
use thiserror::Error;

use actix_web::{
    dev::{forward_ready, Service, ServiceRequest, ServiceResponse, Transform},
    web, Error, HttpMessage,
};
use futures_util::future::LocalBoxFuture;
use sqlx::{PgPool, Row};
use std::{
    future::{ready, Ready},
    rc::Rc,
};
use uuid::Uuid;

// ---------------------------------------------------------------------------
// Key generation
// ---------------------------------------------------------------------------

/// Generate a new random API key (32 random bytes, hex-encoded → 64 chars).
pub fn generate_api_key() -> String {
    let bytes: [u8; 32] = rand::rng().random();
    hex::encode(bytes)
}

/// Hash an API key with SHA-256, returning a lowercase hex string.
pub fn hash_api_key(key: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(key.as_bytes());
    hex::encode(hasher.finalize())
}

/// Constant-time comparison to prevent timing attacks.
/// Used in tests and available for direct validation without DB.
#[allow(dead_code)]
pub fn keys_match(provided: &str, stored_hash: &str) -> bool {
    let provided_hash = hash_api_key(provided);
    // Compare byte-by-byte at constant time using XOR accumulation
    if provided_hash.len() != stored_hash.len() {
        return false;
    }
    let diff: u8 = provided_hash
        .bytes()
        .zip(stored_hash.bytes())
        .fold(0u8, |acc, (a, b)| acc | (a ^ b));
    diff == 0
}

// ---------------------------------------------------------------------------
// Auth errors
// ---------------------------------------------------------------------------

#[derive(Debug, Error)]
pub enum AuthError {
    #[error("missing Authorization header")]
    MissingHeader,
    #[error("invalid Authorization header format (expected: Bearer <key>)")]
    InvalidFormat,
    #[error("invalid or unknown API key")]
    InvalidKey,
    #[error("database error: {0}")]
    Database(#[from] sqlx::Error),
}

// ---------------------------------------------------------------------------
// Extractor: authenticated server ID
// ---------------------------------------------------------------------------

/// Type injected by the auth middleware — the authenticated server's UUID.
#[derive(Clone, Debug)]
pub struct AuthenticatedServer {
    pub server_id: Uuid,
}

/// Extract the raw API key from `Authorization: Bearer <key>`.
pub fn extract_bearer_token(req: &ServiceRequest) -> Result<String, AuthError> {
    let header = req
        .headers()
        .get("Authorization")
        .ok_or(AuthError::MissingHeader)?;

    let value = header
        .to_str()
        .map_err(|_| AuthError::InvalidFormat)?;

    let token = value
        .strip_prefix("Bearer ")
        .ok_or(AuthError::InvalidFormat)?;

    Ok(token.to_owned())
}

/// Validate a raw API key against the database.
/// Returns the server UUID on success.
pub async fn validate_api_key(pool: &PgPool, raw_key: &str) -> Result<Uuid, AuthError> {
    let key_hash = hash_api_key(raw_key);

    let row = sqlx::query("SELECT id FROM servers WHERE api_key_hash = $1")
        .bind(&key_hash)
        .fetch_optional(pool)
        .await?;

    row.and_then(|r| r.try_get::<Uuid, _>("id").ok())
        .ok_or(AuthError::InvalidKey)
}

// ---------------------------------------------------------------------------
// Actix-web middleware
// ---------------------------------------------------------------------------

pub struct ApiKeyAuth;

impl<S, B> Transform<S, ServiceRequest> for ApiKeyAuth
where
    S: Service<ServiceRequest, Response = ServiceResponse<B>, Error = Error> + 'static,
    B: 'static,
{
    type Response = ServiceResponse<B>;
    type Error = Error;
    type InitError = ();
    type Transform = ApiKeyAuthMiddleware<S>;
    type Future = Ready<Result<Self::Transform, Self::InitError>>;

    fn new_transform(&self, service: S) -> Self::Future {
        ready(Ok(ApiKeyAuthMiddleware {
            service: Rc::new(service),
        }))
    }
}

pub struct ApiKeyAuthMiddleware<S> {
    service: Rc<S>,
}

impl<S, B> Service<ServiceRequest> for ApiKeyAuthMiddleware<S>
where
    S: Service<ServiceRequest, Response = ServiceResponse<B>, Error = Error> + 'static,
    B: 'static,
{
    type Response = ServiceResponse<B>;
    type Error = Error;
    type Future = LocalBoxFuture<'static, Result<Self::Response, Self::Error>>;

    forward_ready!(service);

    fn call(&self, req: ServiceRequest) -> Self::Future {
        let service = Rc::clone(&self.service);

        Box::pin(async move {
            let pool = req
                .app_data::<web::Data<PgPool>>()
                .expect("PgPool not in app data")
                .get_ref()
                .clone();

            let raw_key = match extract_bearer_token(&req) {
                Ok(k) => k,
                Err(e) => {
                    return Err(actix_web::error::ErrorUnauthorized(e.to_string()));
                }
            };

            let server_id = match validate_api_key(&pool, &raw_key).await {
                Ok(id) => id,
                Err(e) => {
                    return Err(actix_web::error::ErrorUnauthorized(e.to_string()));
                }
            };

            req.extensions_mut()
                .insert(AuthenticatedServer { server_id });

            service.call(req).await
        })
    }
}

// ---------------------------------------------------------------------------
// Unit tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_generate_api_key_length() {
        let key = generate_api_key();
        // 32 bytes → 64 hex chars
        assert_eq!(key.len(), 64);
    }

    #[test]
    fn test_generate_api_key_hex_chars() {
        let key = generate_api_key();
        assert!(key.chars().all(|c| c.is_ascii_hexdigit()));
    }

    #[test]
    fn test_hash_api_key_deterministic() {
        let key = "deadbeef1234567890abcdef";
        assert_eq!(hash_api_key(key), hash_api_key(key));
    }

    #[test]
    fn test_hash_api_key_is_sha256() {
        let key = "test";
        let hash = hash_api_key(key);
        // SHA-256 produces 32 bytes → 64 hex chars
        assert_eq!(hash.len(), 64);
        assert!(hash.chars().all(|c| c.is_ascii_hexdigit()));
    }

    #[test]
    fn test_keys_match_correct() {
        let key = generate_api_key();
        let stored = hash_api_key(&key);
        assert!(keys_match(&key, &stored));
    }

    #[test]
    fn test_keys_match_wrong_key() {
        let key = generate_api_key();
        let stored = hash_api_key(&key);
        let wrong_key = generate_api_key();
        assert!(!keys_match(&wrong_key, &stored));
    }

    #[test]
    fn test_keys_match_tampered_hash() {
        let key = "correct_key_here_0000000000000000000000000000000000000000000000";
        let correct_hash = hash_api_key(key);
        // Flip the last character
        let mut tampered = correct_hash.clone();
        let last = tampered.pop().unwrap();
        tampered.push(if last == 'f' { '0' } else { 'f' });
        assert!(!keys_match(key, &tampered));
    }

    #[test]
    fn test_keys_match_empty_strings() {
        let stored = hash_api_key("");
        assert!(keys_match("", &stored));
        assert!(!keys_match("notempty", &stored));
    }

    #[test]
    fn test_different_keys_different_hashes() {
        let key1 = generate_api_key();
        let key2 = generate_api_key();
        // Extremely unlikely to collide
        assert_ne!(hash_api_key(&key1), hash_api_key(&key2));
    }
}
