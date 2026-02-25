use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

// ---------------------------------------------------------------------------
// Server registration
// ---------------------------------------------------------------------------

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Server {
    pub id: Uuid,
    pub name: String,
    pub player_count: i32,
    pub created_at: DateTime<Utc>,
    pub last_seen: DateTime<Utc>,
    pub last_submission_at: Option<DateTime<Utc>>,
    pub last_submission_item_count: Option<i32>,
}

#[derive(Debug, Deserialize)]
pub struct RegisterServerRequest {
    pub name: String,
}

#[derive(Debug, Serialize)]
pub struct RegisterServerResponse {
    pub server_id: Uuid,
    /// Raw API key — shown exactly once. Not stored; only the hash is stored.
    pub api_key: String,
}

// ---------------------------------------------------------------------------
// Price submissions
// ---------------------------------------------------------------------------

#[derive(Debug, Serialize, Deserialize)]
pub struct SubmitPricesRequest {
    pub item_names: Vec<String>,
    pub ratio_matrix: Vec<Vec<f64>>,
    pub player_count: i32,
}

#[derive(Debug, Serialize)]
pub struct SubmitPricesResponse {
    pub success: bool,
    pub items_processed: usize,
}

// ---------------------------------------------------------------------------
// True prices
// ---------------------------------------------------------------------------

#[derive(Debug, Serialize, Clone)]
pub struct TruePriceEntry {
    pub item: String,
    pub price: f64,
    pub confidence: f64,
    pub servers: i32,
}

#[derive(Debug, Serialize)]
pub struct TruePricesResponse {
    pub prices: Vec<TruePriceEntry>,
    pub last_updated: Option<DateTime<Utc>>,
}

// ---------------------------------------------------------------------------
// Price history
// ---------------------------------------------------------------------------

#[derive(Debug, Serialize)]
pub struct PriceHistoryPoint {
    pub price: f64,
    pub server_count: i32,
    pub timestamp: DateTime<Utc>,
}

#[derive(Debug, Serialize)]
pub struct PriceHistoryResponse {
    pub item: String,
    pub history: Vec<PriceHistoryPoint>,
}

// ---------------------------------------------------------------------------
// Exchange rates
// ---------------------------------------------------------------------------

#[derive(Debug, Serialize)]
pub struct ExchangeRateEntry {
    pub server_id: Uuid,
    pub name: String,
    /// Multiplier vs true prices. If a server's average is 1.5×, players pay
    /// 1.5× the "true" price — their currency is worth less.
    pub rate: f64,
    pub player_count: i32,
    pub last_seen: DateTime<Utc>,
}

#[derive(Debug, Serialize)]
pub struct ExchangeRatesResponse {
    pub base: String,
    pub rates: Vec<ExchangeRateEntry>,
}

// ---------------------------------------------------------------------------
// Generic error response
// ---------------------------------------------------------------------------

#[derive(Debug, Serialize)]
pub struct ErrorResponse {
    pub error: String,
}

impl ErrorResponse {
    pub fn new(msg: impl Into<String>) -> Self {
        Self { error: msg.into() }
    }
}
