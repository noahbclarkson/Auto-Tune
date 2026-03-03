//! Auction house order management endpoints.
//!
//! POST /api/orders - Place a new limit order
//! DELETE /api/orders/:id - Cancel an order
//! GET /api/orders - List open orders (with optional player_id filter)
//! GET /api/orderbook/:item_id - Get current order book for an item
//! GET /api/orderbook/:item_id/depth - Get order book depth with market summary
//! GET /api/trades - Get trade history

use actix_web::{web, HttpResponse, Responder};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tokio::sync::RwLock;
use uuid::Uuid;

use crate::matching::{MatchError, MatchingEngine, NewOrder, Order, OrderSide, TICK_SIZE};
use crate::models::{
    DepthLevel, ErrorResponse, OrderBookDepthResponse, TradeHistoryQuery, TradeHistoryResponse,
    TradeRecord,
};

/// Shared state for the matching engine
pub type MatchingEngineState = Arc<RwLock<MatchingEngine>>;

// ---------------------------------------------------------------------------
// Request/Response types
// ---------------------------------------------------------------------------

#[derive(Debug, Deserialize, Serialize)]
pub struct PlaceOrderRequest {
    pub server_id: Uuid,
    pub item_id: String,
    pub player_id: String,
    pub price: f64,
    pub quantity: i32,
    pub side: String, // "buy" or "sell"
}

#[derive(Debug, Serialize)]
pub struct PlaceOrderResponse {
    pub order: Order,
}

#[derive(Debug, Deserialize)]
pub struct ListOrdersQuery {
    pub player_id: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct ListOrdersResponse {
    pub orders: Vec<Order>,
}

#[derive(Debug, Serialize)]
pub struct OrderBookEntry {
    pub price: f64,
    pub quantity: i32,
    pub orders: i32,
}

#[derive(Debug, Serialize)]
pub struct OrderBookResponse {
    pub item_id: String,
    pub buys: Vec<OrderBookEntry>,
    pub sells: Vec<OrderBookEntry>,
}

// ---------------------------------------------------------------------------
// Error helpers
// ---------------------------------------------------------------------------

fn match_error_to_response(err: MatchError) -> HttpResponse {
    match err {
        MatchError::InvalidPrice | MatchError::InvalidQuantity => {
            HttpResponse::BadRequest().json(ErrorResponse::new(err.to_string()))
        }
        MatchError::InvalidOrderSide(_) | MatchError::InvalidOrderStatus(_) => {
            HttpResponse::BadRequest().json(ErrorResponse::new(err.to_string()))
        }
        MatchError::OrderNotFound(_) => {
            HttpResponse::NotFound().json(ErrorResponse::new(err.to_string()))
        }
        MatchError::OrderNotActive(_) => {
            HttpResponse::BadRequest().json(ErrorResponse::new(err.to_string()))
        }
        MatchError::SelfMatch { .. } => {
            HttpResponse::BadRequest().json(ErrorResponse::new(err.to_string()))
        }
        MatchError::InvalidTickSize { .. } => {
            HttpResponse::BadRequest().json(ErrorResponse::new(err.to_string()))
        }
    }
}

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

/// POST /api/orders - Place a new limit order
pub async fn place_order(
    engine: web::Data<MatchingEngineState>,
    req: web::Json<PlaceOrderRequest>,
) -> impl Responder {
    // Validate and parse order side
    let side = match req.side.to_lowercase().parse::<OrderSide>() {
        Ok(s) => s,
        Err(e) => return match_error_to_response(e),
    };

    // Create the new order
    let new_order = NewOrder {
        server_id: req.server_id,
        item_id: req.item_id.clone(),
        player_id: req.player_id.clone(),
        price: req.price,
        quantity: req.quantity,
        side,
    };

    // Place the order through the matching engine
    let mut engine = engine.write().await;
    match engine.place_order(new_order) {
        Ok(order) => HttpResponse::Created().json(PlaceOrderResponse { order }),
        Err(e) => match_error_to_response(e),
    }
}

/// DELETE /api/orders/:id - Cancel an order
pub async fn cancel_order(
    engine: web::Data<MatchingEngineState>,
    path: web::Path<Uuid>,
    query: web::Query<CancelOrderQuery>,
) -> impl Responder {
    let order_id = path.into_inner();

    // Require player_id for authorization
    let player_id = match &query.player_id {
        Some(pid) => pid,
        None => {
            return HttpResponse::BadRequest()
                .json(ErrorResponse::new("player_id query parameter is required"));
        }
    };

    // Cancel the order
    let mut engine = engine.write().await;
    match engine.cancel_order(order_id, player_id) {
        Ok(order) => HttpResponse::Ok().json(order),
        Err(e) => match_error_to_response(e),
    }
}

#[derive(Debug, Deserialize)]
pub struct CancelOrderQuery {
    pub player_id: Option<String>,
}

/// GET /api/orders - List open orders (with optional player_id filter)
pub async fn list_orders(
    engine: web::Data<MatchingEngineState>,
    query: web::Query<ListOrdersQuery>,
) -> impl Responder {
    let engine = engine.read().await;

    // Get all active orders, optionally filtered by player_id
    let orders: Vec<Order> = engine
        .get_all_orders(query.player_id.as_deref())
        .into_iter()
        .cloned()
        .collect();

    HttpResponse::Ok().json(ListOrdersResponse { orders })
}

/// GET /api/orderbook/:item_id - Get current order book for an item
pub async fn get_orderbook(
    engine: web::Data<MatchingEngineState>,
    path: web::Path<String>,
) -> impl Responder {
    let item_id = path.into_inner();
    let engine = engine.read().await;

    let (buys, sells) = engine.get_order_book(&item_id);

    // Aggregate orders by price level
    let mut buy_levels: std::collections::HashMap<i64, (f64, i32, i32)> =
        std::collections::HashMap::new();
    let mut sell_levels: std::collections::HashMap<i64, (f64, i32, i32)> =
        std::collections::HashMap::new();

    for order in buys {
        // Use scaled price as key (multiply by 1e9 for precision)
        let price_key = (order.price * 1e9) as i64;
        let entry = buy_levels.entry(price_key).or_insert((order.price, 0, 0));
        entry.1 += order.remaining_quantity;
        entry.2 += 1;
    }

    for order in sells {
        let price_key = (order.price * 1e9) as i64;
        let entry = sell_levels.entry(price_key).or_insert((order.price, 0, 0));
        entry.1 += order.remaining_quantity;
        entry.2 += 1;
    }

    // Convert to sorted vectors
    let mut buy_levels: Vec<OrderBookEntry> = buy_levels
        .into_values()
        .map(|(price, quantity, orders)| OrderBookEntry {
            price,
            quantity,
            orders,
        })
        .collect();
    buy_levels.sort_by(|a, b| {
        b.price
            .partial_cmp(&a.price)
            .unwrap_or(std::cmp::Ordering::Equal)
    });

    let mut sell_levels: Vec<OrderBookEntry> = sell_levels
        .into_values()
        .map(|(price, quantity, orders)| OrderBookEntry {
            price,
            quantity,
            orders,
        })
        .collect();
    sell_levels.sort_by(|a, b| {
        a.price
            .partial_cmp(&b.price)
            .unwrap_or(std::cmp::Ordering::Equal)
    });

    HttpResponse::Ok().json(OrderBookResponse {
        item_id,
        buys: buy_levels,
        sells: sell_levels,
    })
}

/// GET /api/orderbook/:item_id/depth - Get order book depth with market summary
pub async fn get_orderbook_depth(
    engine: web::Data<MatchingEngineState>,
    path: web::Path<String>,
) -> impl Responder {
    let item_id = path.into_inner();
    let engine = engine.read().await;

    let (buys, sells) = engine.get_order_book(&item_id);

    // Aggregate orders by price level
    let mut buy_levels: std::collections::HashMap<i64, (f64, i32, i32)> =
        std::collections::HashMap::new();
    let mut sell_levels: std::collections::HashMap<i64, (f64, i32, i32)> =
        std::collections::HashMap::new();

    for order in buys {
        let price_key = (order.price * 1e9) as i64;
        let entry = buy_levels.entry(price_key).or_insert((order.price, 0, 0));
        entry.1 += order.remaining_quantity;
        entry.2 += 1;
    }

    for order in sells {
        let price_key = (order.price * 1e9) as i64;
        let entry = sell_levels.entry(price_key).or_insert((order.price, 0, 0));
        entry.1 += order.remaining_quantity;
        entry.2 += 1;
    }

    // Convert to sorted vectors
    let mut bids: Vec<DepthLevel> = buy_levels
        .into_values()
        .map(|(price, quantity, order_count)| DepthLevel {
            price,
            quantity,
            order_count,
        })
        .collect();
    bids.sort_by(|a, b| {
        b.price
            .partial_cmp(&a.price)
            .unwrap_or(std::cmp::Ordering::Equal)
    });

    let mut asks: Vec<DepthLevel> = sell_levels
        .into_values()
        .map(|(price, quantity, order_count)| DepthLevel {
            price,
            quantity,
            order_count,
        })
        .collect();
    asks.sort_by(|a, b| {
        a.price
            .partial_cmp(&b.price)
            .unwrap_or(std::cmp::Ordering::Equal)
    });

    // Calculate market summary
    let (spread, mid_price) = match (bids.first(), asks.first()) {
        (Some(best_bid), Some(best_ask)) => {
            let spread = Some(best_ask.price - best_bid.price);
            let mid_price = Some((best_bid.price + best_ask.price) / 2.0);
            (spread, mid_price)
        }
        _ => (None, None),
    };

    let total_bid_quantity: i32 = bids.iter().map(|l| l.quantity).sum();
    let total_ask_quantity: i32 = asks.iter().map(|l| l.quantity).sum();

    HttpResponse::Ok().json(OrderBookDepthResponse {
        item_id,
        tick_size: TICK_SIZE,
        bids,
        asks,
        spread,
        mid_price,
        total_bid_quantity,
        total_ask_quantity,
    })
}

/// GET /api/trades - Get trade history
pub async fn get_trade_history(
    engine: web::Data<MatchingEngineState>,
    query: web::Query<TradeHistoryQuery>,
) -> impl Responder {
    let engine = engine.read().await;

    // Get fills with item info
    let fills_with_items = engine.get_fills_with_items();

    // Filter by item_id if provided
    let mut trades: Vec<TradeRecord> = fills_with_items
        .into_iter()
        .filter(|(_, item_id, _, _)| {
            query
                .item_id
                .as_ref()
                .is_none_or(|filter| item_id == filter)
        })
        .map(|(fill, item_id, buyer_id, seller_id)| TradeRecord {
            id: fill.id,
            item_id,
            price: fill.price,
            quantity: fill.quantity,
            buyer_id,
            seller_id,
            executed_at: fill.filled_at,
        })
        .collect();

    // Sort by execution time (most recent first)
    trades.sort_by(|a, b| b.executed_at.cmp(&a.executed_at));

    // Apply limit
    let limit = query.limit.unwrap_or(100).min(1000) as usize;
    trades.truncate(limit);

    let count = trades.len();
    HttpResponse::Ok().json(TradeHistoryResponse { trades, count })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use actix_web::{test, App};

    #[actix_web::test]
    async fn test_place_order() {
        let engine = Arc::new(RwLock::new(MatchingEngine::new()));
        let app = test::init_service(
            App::new()
                .app_data(web::Data::new(engine))
                .route("/api/orders", web::post().to(place_order)),
        )
        .await;

        let req = test::TestRequest::post()
            .uri("/api/orders")
            .set_json(&PlaceOrderRequest {
                server_id: Uuid::new_v4(),
                item_id: "DIAMOND".to_string(),
                player_id: "player1".to_string(),
                price: 100.0,
                quantity: 10,
                side: "buy".to_string(),
            })
            .to_request();

        let resp = test::call_service(&app, req).await;
        assert!(resp.status().is_success());
    }
}
