//! Order matching engine for the auction house.
//!
//! Implements a limit order book with price-time priority matching.
//! Buy orders are sorted by price (highest first), then by creation time (oldest first).
//! Sell orders are sorted by price (lowest first), then by creation time (oldest first).

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use thiserror::Error;
use uuid::Uuid;

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/// Order side: buy or sell
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum OrderSide {
    Buy,
    Sell,
}

impl std::fmt::Display for OrderSide {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            OrderSide::Buy => write!(f, "buy"),
            OrderSide::Sell => write!(f, "sell"),
        }
    }
}

impl std::str::FromStr for OrderSide {
    type Err = MatchError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "buy" => Ok(OrderSide::Buy),
            "sell" => Ok(OrderSide::Sell),
            _ => Err(MatchError::InvalidOrderSide(s.to_string())),
        }
    }
}

/// Order status
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum OrderStatus {
    Open,
    PartiallyFilled,
    Filled,
    Cancelled,
}

impl std::fmt::Display for OrderStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            OrderStatus::Open => write!(f, "open"),
            OrderStatus::PartiallyFilled => write!(f, "partially_filled"),
            OrderStatus::Filled => write!(f, "filled"),
            OrderStatus::Cancelled => write!(f, "cancelled"),
        }
    }
}

impl std::str::FromStr for OrderStatus {
    type Err = MatchError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "open" => Ok(OrderStatus::Open),
            "partially_filled" => Ok(OrderStatus::PartiallyFilled),
            "filled" => Ok(OrderStatus::Filled),
            "cancelled" => Ok(OrderStatus::Cancelled),
            _ => Err(MatchError::InvalidOrderStatus(s.to_string())),
        }
    }
}

/// A limit order in the order book
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Order {
    pub id: Uuid,
    pub server_id: Uuid,
    pub item_id: String,
    pub player_id: String,
    pub price: f64,
    pub quantity: i32,
    pub remaining_quantity: i32,
    pub side: OrderSide,
    pub status: OrderStatus,
    pub created_at: DateTime<Utc>,
    pub filled_at: Option<DateTime<Utc>>,
}

impl Order {
    /// Check if this order can still be matched (open or partially filled)
    pub fn is_active(&self) -> bool {
        matches!(
            self.status,
            OrderStatus::Open | OrderStatus::PartiallyFilled
        ) && self.remaining_quantity > 0
    }

    /// How much has been filled so far
    pub fn filled_quantity(&self) -> i32 {
        self.quantity - self.remaining_quantity
    }
}

/// A new order to be placed
#[derive(Debug, Clone, Deserialize)]
pub struct NewOrder {
    pub server_id: Uuid,
    pub item_id: String,
    pub player_id: String,
    pub price: f64,
    pub quantity: i32,
    pub side: OrderSide,
}

/// A fill record when orders are matched
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OrderFill {
    pub id: Uuid,
    pub buy_order_id: Uuid,
    pub sell_order_id: Uuid,
    pub quantity: i32,
    pub price: f64,
    pub filled_at: DateTime<Utc>,
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

#[derive(Debug, Error)]
pub enum MatchError {
    #[error("Invalid price: must be > 0")]
    InvalidPrice,

    #[error("Invalid quantity: must be > 0")]
    InvalidQuantity,

    #[error("Invalid order side: {0}")]
    InvalidOrderSide(String),

    #[error("Invalid order status: {0}")]
    InvalidOrderStatus(String),

    #[error("Order not found: {0}")]
    OrderNotFound(Uuid),

    #[error("Order is not active (status: {0})")]
    OrderNotActive(String),

    #[error("Player {player_id} cannot match against their own order {order_id}")]
    SelfMatch { player_id: String, order_id: Uuid },
}

// ---------------------------------------------------------------------------
// Price-Time Priority Ordering
// ---------------------------------------------------------------------------

/// Wrapper for ordering buy orders: highest price first, then oldest first
#[derive(Debug, Clone, Eq, PartialEq)]
struct BuyOrderKey {
    price: i64,        // Inverted so higher prices sort first
    created_at: i64,   // Nanoseconds since epoch
    order_id: Uuid,    // Tie-breaker for deterministic ordering
}

impl Ord for BuyOrderKey {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        // Higher price = higher priority (compare inverted prices)
        // Earlier time = higher priority
        // Use UUID as final tie-breaker for determinism
        self.price
            .cmp(&other.price)
            .then_with(|| self.created_at.cmp(&other.created_at))
            .then_with(|| self.order_id.cmp(&other.order_id))
    }
}

impl PartialOrd for BuyOrderKey {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

/// Wrapper for ordering sell orders: lowest price first, then oldest first
#[derive(Debug, Clone, Eq, PartialEq)]
struct SellOrderKey {
    price: i64,        // Normal order: lower prices sort first
    created_at: i64,   // Nanoseconds since epoch
    order_id: Uuid,    // Tie-breaker
}

impl Ord for SellOrderKey {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        // Lower price = higher priority
        // Earlier time = higher priority
        self.price
            .cmp(&other.price)
            .then_with(|| self.created_at.cmp(&other.created_at))
            .then_with(|| self.order_id.cmp(&other.order_id))
    }
}

impl PartialOrd for SellOrderKey {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

// Convert f64 price to i64 for deterministic ordering
// We scale by 1e9 to preserve 9 decimal places of precision
fn price_to_sort_key(price: f64) -> i64 {
    // Invert for buy orders so higher prices come first
    (price * 1e9) as i64
}

fn price_to_inverted_sort_key(price: f64) -> i64 {
    // Invert so that higher prices sort before lower prices
    i64::MAX - (price * 1e9) as i64
}

fn timestamp_to_sort_key(ts: DateTime<Utc>) -> i64 {
    ts.timestamp_nanos_opt().unwrap_or(0)
}

// ---------------------------------------------------------------------------
// Matching Engine
// ---------------------------------------------------------------------------

/// The matching engine maintains order books for items and matches orders
/// using price-time priority.
pub struct MatchingEngine {
    /// All orders by ID
    orders: std::collections::HashMap<Uuid, Order>,
    
    /// Buy side order books per item: sorted by price (desc) then time (asc)
    buy_books: std::collections::HashMap<String, BTreeMap<BuyOrderKey, Uuid>>,
    
    /// Sell side order books per item: sorted by price (asc) then time (asc)
    sell_books: std::collections::HashMap<String, BTreeMap<SellOrderKey, Uuid>>,
    
    /// Fill records
    fills: Vec<OrderFill>,
}

impl Default for MatchingEngine {
    fn default() -> Self {
        Self::new()
    }
}

impl MatchingEngine {
    /// Create a new empty matching engine
    pub fn new() -> Self {
        Self {
            orders: std::collections::HashMap::new(),
            buy_books: std::collections::HashMap::new(),
            sell_books: std::collections::HashMap::new(),
            fills: Vec::new(),
        }
    }

    /// Place a new order and immediately attempt to match it
    pub fn place_order(&mut self, new_order: NewOrder) -> Result<Order, MatchError> {
        self.place_order_at(new_order, Utc::now())
    }

    /// Place a new order with a specific timestamp (useful for testing)
    pub fn place_order_at(
        &mut self,
        new_order: NewOrder,
        created_at: DateTime<Utc>,
    ) -> Result<Order, MatchError> {
        // Validate
        if new_order.price <= 0.0 {
            return Err(MatchError::InvalidPrice);
        }
        if new_order.quantity <= 0 {
            return Err(MatchError::InvalidQuantity);
        }

        let order = Order {
            id: Uuid::new_v4(),
            server_id: new_order.server_id,
            item_id: new_order.item_id.clone(),
            player_id: new_order.player_id,
            price: new_order.price,
            quantity: new_order.quantity,
            remaining_quantity: new_order.quantity,
            side: new_order.side,
            status: OrderStatus::Open,
            created_at,
            filled_at: None,
        };

        // Store the order
        let order_id = order.id;
        self.orders.insert(order_id, order.clone());

        // Add to appropriate order book
        match new_order.side {
            OrderSide::Buy => {
                let key = BuyOrderKey {
                    price: price_to_inverted_sort_key(order.price),
                    created_at: timestamp_to_sort_key(order.created_at),
                    order_id,
                };
                self.buy_books
                    .entry(new_order.item_id.clone())
                    .or_default()
                    .insert(key, order_id);
            }
            OrderSide::Sell => {
                let key = SellOrderKey {
                    price: price_to_sort_key(order.price),
                    created_at: timestamp_to_sort_key(order.created_at),
                    order_id,
                };
                self.sell_books
                    .entry(new_order.item_id.clone())
                    .or_default()
                    .insert(key, order_id);
            }
        }

        // Attempt to match
        let fills = self.match_orders(&new_order.item_id);

        // Return the updated order (may have been modified by matching)
        let updated_order = self.orders.get(&order_id).cloned().unwrap_or(order);
        tracing::debug!(
            order_id = %order_id,
            fills = fills.len(),
            remaining = updated_order.remaining_quantity,
            "Order placed and matched"
        );

        Ok(updated_order)
    }

    /// Match orders for a specific item using price-time priority
    /// Returns the fills that occurred
    pub fn match_orders(&mut self, item_id: &str) -> Vec<OrderFill> {
        self.match_orders_at(item_id, Utc::now())
    }

    /// Match orders with a specific timestamp (useful for testing)
    pub fn match_orders_at(&mut self, item_id: &str, now: DateTime<Utc>) -> Vec<OrderFill> {
        let mut fills = Vec::new();
        
        let buy_book = self.buy_books.get(item_id);
        let sell_book = self.sell_books.get(item_id);
        
        // Need both sides to match
        let (Some(buy_book), Some(sell_book)) = (buy_book, sell_book) else {
            return fills;
        };

        // Get active order IDs from both sides
        let mut buy_order_ids: Vec<Uuid> = buy_book.values().copied().collect();
        let mut sell_order_ids: Vec<Uuid> = sell_book.values().copied().collect();

        // Sort buy orders: highest price first, then oldest first
        buy_order_ids.sort_by(|a, b| {
            let order_a = self.orders.get(a);
            let order_b = self.orders.get(b);
            match (order_a, order_b) {
                (Some(a), Some(b)) => {
                    // Higher price first
                    b.price
                        .partial_cmp(&a.price)
                        .unwrap_or(std::cmp::Ordering::Equal)
                        // Then older first
                        .then_with(|| a.created_at.cmp(&b.created_at))
                }
                _ => std::cmp::Ordering::Equal,
            }
        });

        // Sort sell orders: lowest price first, then oldest first
        sell_order_ids.sort_by(|a, b| {
            let order_a = self.orders.get(a);
            let order_b = self.orders.get(b);
            match (order_a, order_b) {
                (Some(a), Some(b)) => {
                    // Lower price first
                    a.price
                        .partial_cmp(&b.price)
                        .unwrap_or(std::cmp::Ordering::Equal)
                        // Then older first
                        .then_with(|| a.created_at.cmp(&b.created_at))
                }
                _ => std::cmp::Ordering::Equal,
            }
        });

        // Match buy orders against sell orders
        for buy_order_id in buy_order_ids {
            let buy_order = match self.orders.get(&buy_order_id) {
                Some(o) if o.is_active() => o.clone(),
                _ => continue,
            };

            for sell_order_id in &sell_order_ids {
                let sell_order = match self.orders.get(sell_order_id) {
                    Some(o) if o.is_active() => o.clone(),
                    _ => continue,
                };

                // Skip if same player (can't match against yourself)
                if buy_order.player_id == sell_order.player_id {
                    continue;
                }

                // Check if prices cross: buy price >= sell price
                if buy_order.price < sell_order.price {
                    // No more matches possible for this buy order
                    // (since sell orders are sorted by price ascending)
                    break;
                }

                // Match! Execute at the maker's price (the order already in the book)
                // The sell order is the "maker" if it was placed first, otherwise buy is maker
                // For simplicity, we use the sell order's price as the execution price
                // (This is a common convention: aggressive order pays the maker's price)
                let execution_price = sell_order.price;
                let match_quantity = buy_order
                    .remaining_quantity
                    .min(sell_order.remaining_quantity);

                // Create the fill
                let fill = OrderFill {
                    id: Uuid::new_v4(),
                    buy_order_id: buy_order.id,
                    sell_order_id: sell_order.id,
                    quantity: match_quantity,
                    price: execution_price,
                    filled_at: now,
                };
                fills.push(fill.clone());

                // Update buy order
                if let Some(buy) = self.orders.get_mut(&buy_order_id) {
                    buy.remaining_quantity -= match_quantity;
                    buy.status = if buy.remaining_quantity == 0 {
                        buy.filled_at = Some(now);
                        OrderStatus::Filled
                    } else {
                        OrderStatus::PartiallyFilled
                    };
                }

                // Update sell order
                if let Some(sell) = self.orders.get_mut(sell_order_id) {
                    sell.remaining_quantity -= match_quantity;
                    sell.status = if sell.remaining_quantity == 0 {
                        sell.filled_at = Some(now);
                        OrderStatus::Filled
                    } else {
                        OrderStatus::PartiallyFilled
                    };
                }

                // Store the fill
                self.fills.push(fill);

                // If buy order is fully filled, move to next buy order
                if self
                    .orders
                    .get(&buy_order_id)
                    .map(|o| o.remaining_quantity == 0)
                    .unwrap_or(false)
                {
                    break;
                }
            }
        }

        // Clean up filled orders from the books
        self.cleanup_filled_orders(item_id);

        fills
    }

    /// Remove filled/cancelled orders from the order books
    fn cleanup_filled_orders(&mut self, item_id: &str) {
        if let Some(buy_book) = self.buy_books.get_mut(item_id) {
            let filled_keys: Vec<_> = buy_book
                .iter()
                .filter(|(_, id)| {
                    self.orders
                        .get(id)
                        .map(|o| !o.is_active())
                        .unwrap_or(true)
                })
                .map(|(k, _)| k.clone())
                .collect();
            for key in filled_keys {
                buy_book.remove(&key);
            }
        }

        if let Some(sell_book) = self.sell_books.get_mut(item_id) {
            let filled_keys: Vec<_> = sell_book
                .iter()
                .filter(|(_, id)| {
                    self.orders
                        .get(id)
                        .map(|o| !o.is_active())
                        .unwrap_or(true)
                })
                .map(|(k, _)| k.clone())
                .collect();
            for key in filled_keys {
                sell_book.remove(&key);
            }
        }
    }

    /// Get an order by ID
    pub fn get_order(&self, id: &Uuid) -> Option<&Order> {
        self.orders.get(id)
    }

    /// Get all active orders for an item
    pub fn get_order_book(&self, item_id: &str) -> (Vec<&Order>, Vec<&Order>) {
        let buys = self
            .orders
            .values()
            .filter(|o| o.item_id == item_id && o.side == OrderSide::Buy && o.is_active())
            .collect::<Vec<_>>();

        let sells = self
            .orders
            .values()
            .filter(|o| o.item_id == item_id && o.side == OrderSide::Sell && o.is_active())
            .collect::<Vec<_>>();

        (buys, sells)
    }

    /// Get all fills
    pub fn get_fills(&self) -> &[OrderFill] {
        &self.fills
    }

    /// Get all orders (optionally filtered by player_id)
    pub fn get_all_orders(&self, player_id: Option<&str>) -> Vec<&Order> {
        self.orders
            .values()
            .filter(|o| {
                o.is_active()
                    && player_id.map_or(true, |pid| o.player_id == pid)
            })
            .collect()
    }

    /// Cancel an order
    pub fn cancel_order(&mut self, order_id: Uuid, player_id: &str) -> Result<Order, MatchError> {
        // First, verify ownership and check if cancellable
        {
            let order = self
                .orders
                .get(&order_id)
                .ok_or(MatchError::OrderNotFound(order_id))?;

            // Verify ownership
            if order.player_id != player_id {
                return Err(MatchError::OrderNotFound(order_id));
            }

            // Check if cancellable
            if !order.is_active() {
                return Err(MatchError::OrderNotActive(order.status.to_string()));
            }
        }

        // Now modify the order
        let order = self.orders.get_mut(&order_id).unwrap();
        order.status = OrderStatus::Cancelled;
        let item_id = order.item_id.clone();
        
        // Remove from order books
        self.cleanup_filled_orders(&item_id);

        Ok(self.orders.get(&order_id).unwrap().clone())
    }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    fn test_server_id() -> Uuid {
        Uuid::parse_str("00000000-0000-0000-0000-000000000001").unwrap()
    }

    fn test_player(id: &str) -> String {
        id.to_string()
    }

    fn base_time() -> DateTime<Utc> {
        DateTime::parse_from_rfc3339("2024-01-01T00:00:00Z")
            .unwrap()
            .with_timezone(&Utc)
    }

    #[test]
    fn test_place_buy_order() {
        let mut engine = MatchingEngine::new();
        let order = engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("alice"),
                price: 100.0,
                quantity: 10,
                side: OrderSide::Buy,
            })
            .unwrap();

        assert_eq!(order.status, OrderStatus::Open);
        assert_eq!(order.remaining_quantity, 10);
        assert!(order.is_active());
    }

    #[test]
    fn test_place_sell_order() {
        let mut engine = MatchingEngine::new();
        let order = engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("bob"),
                price: 100.0,
                quantity: 10,
                side: OrderSide::Sell,
            })
            .unwrap();

        assert_eq!(order.status, OrderStatus::Open);
        assert_eq!(order.remaining_quantity, 10);
    }

    #[test]
    fn test_invalid_price() {
        let mut engine = MatchingEngine::new();
        let result = engine.place_order(NewOrder {
            server_id: test_server_id(),
            item_id: "DIAMOND".to_string(),
            player_id: test_player("alice"),
            price: 0.0,
            quantity: 10,
            side: OrderSide::Buy,
        });

        assert!(matches!(result, Err(MatchError::InvalidPrice)));
    }

    #[test]
    fn test_invalid_quantity() {
        let mut engine = MatchingEngine::new();
        let result = engine.place_order(NewOrder {
            server_id: test_server_id(),
            item_id: "DIAMOND".to_string(),
            player_id: test_player("alice"),
            price: 100.0,
            quantity: 0,
            side: OrderSide::Buy,
        });

        assert!(matches!(result, Err(MatchError::InvalidQuantity)));
    }

    #[test]
    fn test_simple_match() {
        let mut engine = MatchingEngine::new();
        let t0 = base_time();
        let t1 = t0 + chrono::Duration::seconds(1);

        // Alice places a sell order first (maker)
        let sell = engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("alice"),
                    price: 100.0,
                    quantity: 10,
                    side: OrderSide::Sell,
                },
                t0,
            )
            .unwrap();

        // Bob places a buy order that crosses (taker)
        let buy = engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("bob"),
                    price: 105.0, // Willing to pay more than sell price
                    quantity: 5,
                    side: OrderSide::Buy,
                },
                t1,
            )
            .unwrap();

        // Check the fill
        let fills = engine.get_fills();
        assert_eq!(fills.len(), 1);
        assert_eq!(fills[0].quantity, 5);
        assert_eq!(fills[0].price, 100.0); // Maker's price

        // Check order statuses
        let sell = engine.get_order(&sell.id).unwrap();
        assert_eq!(sell.status, OrderStatus::PartiallyFilled);
        assert_eq!(sell.remaining_quantity, 5);

        let buy = engine.get_order(&buy.id).unwrap();
        assert_eq!(buy.status, OrderStatus::Filled);
        assert_eq!(buy.remaining_quantity, 0);
    }

    #[test]
    fn test_full_match() {
        let mut engine = MatchingEngine::new();
        let t0 = base_time();
        let t1 = t0 + chrono::Duration::seconds(1);

        // Alice sells 10 at 100
        let sell = engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("alice"),
                    price: 100.0,
                    quantity: 10,
                    side: OrderSide::Sell,
                },
                t0,
            )
            .unwrap();

        // Bob buys 10 at 105
        let buy = engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("bob"),
                    price: 105.0,
                    quantity: 10,
                    side: OrderSide::Buy,
                },
                t1,
            )
            .unwrap();

        // Both should be fully filled
        let sell = engine.get_order(&sell.id).unwrap();
        assert_eq!(sell.status, OrderStatus::Filled);
        assert!(sell.filled_at.is_some());

        let buy = engine.get_order(&buy.id).unwrap();
        assert_eq!(buy.status, OrderStatus::Filled);
        assert!(buy.filled_at.is_some());
    }

    #[test]
    fn test_no_match_different_items() {
        let mut engine = MatchingEngine::new();

        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("alice"),
                price: 100.0,
                quantity: 10,
                side: OrderSide::Sell,
            })
            .unwrap();

        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "IRON_INGOT".to_string(),
                player_id: test_player("bob"),
                price: 105.0,
                quantity: 10,
                side: OrderSide::Buy,
            })
            .unwrap();

        // No fills
        assert_eq!(engine.get_fills().len(), 0);
    }

    #[test]
    fn test_no_match_prices_dont_cross() {
        let mut engine = MatchingEngine::new();

        // Sell at 100
        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("alice"),
                price: 100.0,
                quantity: 10,
                side: OrderSide::Sell,
            })
            .unwrap();

        // Buy at 90 (below sell price)
        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("bob"),
                price: 90.0,
                quantity: 10,
                side: OrderSide::Buy,
            })
            .unwrap();

        // No fills
        assert_eq!(engine.get_fills().len(), 0);
    }

    #[test]
    fn test_no_self_match() {
        let mut engine = MatchingEngine::new();

        // Alice places both sides
        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("alice"),
                price: 100.0,
                quantity: 10,
                side: OrderSide::Sell,
            })
            .unwrap();

        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("alice"), // Same player!
                price: 105.0,
                quantity: 10,
                side: OrderSide::Buy,
            })
            .unwrap();

        // No fills - can't match against yourself
        assert_eq!(engine.get_fills().len(), 0);
    }

    #[test]
    fn test_price_time_priority_buy() {
        let mut engine = MatchingEngine::new();
        let t0 = base_time();
        let t1 = t0 + chrono::Duration::seconds(1);
        let t2 = t0 + chrono::Duration::seconds(2);

        // Two buy orders: higher price should match first
        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("bob1"),
                    price: 100.0, // Lower price
                    quantity: 5,
                    side: OrderSide::Buy,
                },
                t0,
            )
            .unwrap();

        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("bob2"),
                    price: 110.0, // Higher price - should match first
                    quantity: 5,
                    side: OrderSide::Buy,
                },
                t1,
            )
            .unwrap();

        // Sell order for 5 - should match bob2 (higher price)
        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("alice"),
                    price: 90.0,
                    quantity: 5,
                    side: OrderSide::Sell,
                },
                t2,
            )
            .unwrap();

        let fills = engine.get_fills();
        assert_eq!(fills.len(), 1);
        assert_eq!(fills[0].price, 90.0);

        // bob2 should be filled, bob1 should still be open
        let (buys, _) = engine.get_order_book("DIAMOND");
        assert_eq!(buys.len(), 1);
        assert_eq!(buys[0].player_id, "bob1");
    }

    #[test]
    fn test_price_time_priority_sell() {
        let mut engine = MatchingEngine::new();
        let t0 = base_time();
        let t1 = t0 + chrono::Duration::seconds(1);
        let t2 = t0 + chrono::Duration::seconds(2);

        // Two sell orders: lower price should match first
        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("alice1"),
                    price: 110.0, // Higher price
                    quantity: 5,
                    side: OrderSide::Sell,
                },
                t0,
            )
            .unwrap();

        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("alice2"),
                    price: 100.0, // Lower price - should match first
                    quantity: 5,
                    side: OrderSide::Sell,
                },
                t1,
            )
            .unwrap();

        // Buy order for 5 - should match alice2 (lower price)
        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("bob"),
                    price: 120.0,
                    quantity: 5,
                    side: OrderSide::Buy,
                },
                t2,
            )
            .unwrap();

        let fills = engine.get_fills();
        assert_eq!(fills.len(), 1);
        assert_eq!(fills[0].price, 100.0); // alice2's price

        // alice2 should be filled, alice1 should still be open
        let (_, sells) = engine.get_order_book("DIAMOND");
        assert_eq!(sells.len(), 1);
        assert_eq!(sells[0].player_id, "alice1");
    }

    #[test]
    fn test_time_priority_same_price() {
        let mut engine = MatchingEngine::new();
        let t0 = base_time();
        let t1 = t0 + chrono::Duration::seconds(1);
        let t2 = t0 + chrono::Duration::seconds(2);

        // Two sell orders at same price: earlier should match first
        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("alice1"),
                    price: 100.0,
                    quantity: 5,
                    side: OrderSide::Sell,
                },
                t0,
            )
            .unwrap();

        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("alice2"),
                    price: 100.0,
                    quantity: 5,
                    side: OrderSide::Sell,
                },
                t1,
            )
            .unwrap();

        // Buy order - should match alice1 (earlier)
        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("bob"),
                    price: 110.0,
                    quantity: 5,
                    side: OrderSide::Buy,
                },
                t2,
            )
            .unwrap();

        let fills = engine.get_fills();
        assert_eq!(fills.len(), 1);
        // The buy order should be fully filled
        let (buys, _) = engine.get_order_book("DIAMOND");
        assert_eq!(buys.len(), 0); // Buy order is fully filled

        // alice1 should be filled, alice2 should still be open
        let (_, sells) = engine.get_order_book("DIAMOND");
        assert_eq!(sells.len(), 1);
        assert_eq!(sells[0].player_id, "alice2");
    }

    #[test]
    fn test_partial_fill_chain() {
        let mut engine = MatchingEngine::new();
        let t0 = base_time();
        let t1 = t0 + chrono::Duration::seconds(1);
        let t2 = t0 + chrono::Duration::seconds(2);

        // Alice sells 10 at 100
        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("alice"),
                    price: 100.0,
                    quantity: 10,
                    side: OrderSide::Sell,
                },
                t0,
            )
            .unwrap();

        // Bob buys 15 at 105 - will partially fill against alice
        let buy = engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("bob"),
                    price: 105.0,
                    quantity: 15,
                    side: OrderSide::Buy,
                },
                t1,
            )
            .unwrap();
        let buy_id = buy.id;

        // Should have one fill for 10
        let fills = engine.get_fills();
        assert_eq!(fills.len(), 1);
        assert_eq!(fills[0].quantity, 10);

        // Bob's order should be partially filled
        let buy = engine.get_order(&buy_id).unwrap();
        assert_eq!(buy.status, OrderStatus::PartiallyFilled);
        assert_eq!(buy.remaining_quantity, 5);

        // Carol sells 5 at 100 - should match bob's remaining
        engine
            .place_order_at(
                NewOrder {
                    server_id: test_server_id(),
                    item_id: "DIAMOND".to_string(),
                    player_id: test_player("carol"),
                    price: 100.0,
                    quantity: 5,
                    side: OrderSide::Sell,
                },
                t2,
            )
            .unwrap();

        // Now should have 2 fills
        let fills = engine.get_fills();
        assert_eq!(fills.len(), 2);
        assert_eq!(fills[1].quantity, 5);

        // Bob should now be fully filled
        let buy = engine.get_order(&buy_id).unwrap();
        assert_eq!(buy.status, OrderStatus::Filled);
        assert_eq!(buy.remaining_quantity, 0);
    }

    #[test]
    fn test_cancel_order() {
        let mut engine = MatchingEngine::new();

        let order = engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("alice"),
                price: 100.0,
                quantity: 10,
                side: OrderSide::Sell,
            })
            .unwrap();

        // Cancel it
        let cancelled = engine.cancel_order(order.id, "alice").unwrap();
        assert_eq!(cancelled.status, OrderStatus::Cancelled);

        // Should not be active
        let order = engine.get_order(&order.id).unwrap();
        assert!(!order.is_active());

        // Can't cancel again
        let result = engine.cancel_order(order.id, "alice");
        assert!(matches!(result, Err(MatchError::OrderNotActive(_))));
    }

    #[test]
    fn test_cancel_wrong_player() {
        let mut engine = MatchingEngine::new();

        let order = engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("alice"),
                price: 100.0,
                quantity: 10,
                side: OrderSide::Sell,
            })
            .unwrap();

        // Bob tries to cancel Alice's order
        let result = engine.cancel_order(order.id, "bob");
        assert!(matches!(result, Err(MatchError::OrderNotFound(_))));
    }

    #[test]
    fn test_order_book_snapshot() {
        let mut engine = MatchingEngine::new();

        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("bob1"),
                price: 100.0,
                quantity: 5,
                side: OrderSide::Buy,
            })
            .unwrap();

        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("bob2"),
                price: 95.0,
                quantity: 10,
                side: OrderSide::Buy,
            })
            .unwrap();

        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("alice1"),
                price: 110.0,
                quantity: 8,
                side: OrderSide::Sell,
            })
            .unwrap();

        let (buys, sells) = engine.get_order_book("DIAMOND");

        assert_eq!(buys.len(), 2);
        assert_eq!(sells.len(), 1);
        assert_eq!(sells[0].price, 110.0);
    }

    #[test]
    fn test_exact_price_match() {
        let mut engine = MatchingEngine::new();

        // Sell at exactly 100
        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("alice"),
                price: 100.0,
                quantity: 10,
                side: OrderSide::Sell,
            })
            .unwrap();

        // Buy at exactly 100
        engine
            .place_order(NewOrder {
                server_id: test_server_id(),
                item_id: "DIAMOND".to_string(),
                player_id: test_player("bob"),
                price: 100.0,
                quantity: 10,
                side: OrderSide::Buy,
            })
            .unwrap();

        // Should match (buy >= sell)
        assert_eq!(engine.get_fills().len(), 1);
    }
}
