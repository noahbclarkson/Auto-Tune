//! Simple in-memory token-bucket rate limiter per IP address.
//!
//! Each IP gets a bucket of `capacity` tokens, refilling at `refill_per_sec`.
//! A request costs 1 token. IPs with no tokens left get 429 Too Many Requests.

use std::collections::HashMap;
use std::sync::Arc;
use std::time::{Duration, Instant};

use tokio::sync::RwLock;

/// Token-bucket state for a single IP.
struct Bucket {
    /// Tokens available (can be fractional)
    tokens: f64,
    /// Last refill timestamp
    last_refill: Instant,
}

impl Bucket {
    fn refill(&mut self, capacity: f64, refill_per_sec: f64) {
        let now = Instant::now();
        let elapsed = now.duration_since(self.last_refill).as_secs_f64();
        self.tokens = (self.tokens + elapsed * refill_per_sec).min(capacity);
        self.last_refill = now;
    }

    /// Try to consume 1 token. Returns true if allowed.
    fn try_consume(&mut self, capacity: f64, refill_per_sec: f64) -> bool {
        self.refill(capacity, refill_per_sec);
        if self.tokens >= 1.0 {
            self.tokens -= 1.0;
            true
        } else {
            false
        }
    }
}

/// Rate limiter configuration.
#[derive(Debug, Clone)]
pub struct RateLimitConfig {
    /// Maximum tokens per IP (bucket capacity)
    pub capacity: f64,
    /// Tokens added per second (refill rate)
    pub refill_per_sec: f64,
    /// Clean up IPs idle longer than this
    pub idle_timeout: Duration,
}

impl Default for RateLimitConfig {
    fn default() -> Self {
        Self {
            capacity: 30.0,
            refill_per_sec: 10.0,
            idle_timeout: Duration::from_secs(300),
        }
    }
}

impl RateLimitConfig {
    /// Registration: 10 requests/minute per IP, with a burst of 10.
    pub fn fast() -> Self {
        Self {
            capacity: 10.0,
            refill_per_sec: 10.0 / 60.0,
            idle_timeout: Duration::from_secs(120),
        }
    }

    /// Price submission: 6 requests/minute per IP, with a burst of 6.
    pub fn submit() -> Self {
        Self {
            capacity: 6.0,
            refill_per_sec: 6.0 / 60.0,
            idle_timeout: Duration::from_secs(60),
        }
    }
}

type Inner = Arc<RwLock<HashMap<String, Bucket>>>;

/// Shared rate limiter state.
#[derive(Clone)]
pub struct RateLimiter {
    inner: Inner,
    cfg: RateLimitConfig,
}

impl RateLimiter {
    pub fn new(cfg: RateLimitConfig) -> Self {
        Self {
            inner: Arc::new(RwLock::new(HashMap::new())),
            cfg,
        }
    }

    /// Check if a request from `ip` is allowed. Cleans up stale entries lazily.
    pub async fn check(&self, ip: &str) -> RateLimitResult {
        let mut buckets = self.inner.write().await;

        // Lazy cleanup: remove entries that haven't been used in idle_timeout
        buckets.retain(|_, b| b.last_refill.elapsed() < self.cfg.idle_timeout);

        let bucket = buckets.entry(ip.to_owned()).or_insert_with(|| Bucket {
            tokens: self.cfg.capacity,
            last_refill: Instant::now(),
        });

        let allowed = bucket.try_consume(self.cfg.capacity, self.cfg.refill_per_sec);

        if allowed {
            RateLimitResult::Allowed
        } else {
            RateLimitResult::Limited {
                retry_after_secs: (((1.0 - bucket.tokens) / self.cfg.refill_per_sec).ceil() as u64)
                    .max(1),
            }
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub enum RateLimitResult {
    Allowed,
    Limited { retry_after_secs: u64 },
}

/// Extract client IP from an Actix `HttpRequest`.
pub fn client_ip(req: &actix_web::HttpRequest) -> Option<String> {
    // Check X-Forwarded-For first (behind reverse proxy)
    req.headers()
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .map(|s| s.split(',').next().unwrap_or(s).trim().to_owned())
        .or_else(|| {
            req.connection_info()
                .realip_remote_addr()
                .map(|s| s.to_owned())
        })
}
