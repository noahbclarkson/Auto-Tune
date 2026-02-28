# Auction House Order Book System

## Overview

The auction house system implements a limit order book that allows players to place buy and sell orders for items at prices between the computed true prices. This enables peer-to-peer trading without requiring direct buyer-seller matching at fixed prices.

## Schema Design

### Tables

#### `orders`

Stores individual limit orders placed by players.

| Column              | Type        | Description                                              |
|---------------------|-------------|----------------------------------------------------------|
| `id`                | UUID        | Primary key, auto-generated                              |
| `server_id`         | UUID        | Foreign key to `servers.id`, identifies which server the order was placed on |
| `item_id`           | TEXT        | Item identifier (e.g., "DIAMOND", "IRON_INGOT")         |
| `player_id`         | TEXT        | Minecraft player UUID or username                        |
| `price`             | DOUBLE      | Price per unit (must be > 0)                            |
| `quantity`          | INT         | Total quantity requested/offered                         |
| `remaining_quantity`| INT         | Unfilled quantity remaining (decreases as fills occur)  |
| `side`              | TEXT        | 'buy' or 'sell'                                          |
| `status`            | TEXT        | 'open', 'partially_filled', 'filled', or 'cancelled'    |
| `created_at`        | TIMESTAMPTZ | When the order was placed                                |
| `filled_at`         | TIMESTAMPTZ | When the order was fully filled (NULL if not filled)    |

**Constraints:**
- `price > 0`
- `quantity > 0`
- `remaining_quantity >= 0`
- `remaining_quantity <= quantity`

**Indexes:**
- `idx_orders_item_status`: Efficient queries for active orders by item
- `idx_orders_item_price`: Order book queries sorted by price
- `idx_orders_player`: Query orders by player on a specific server
- `idx_orders_created`: Time-based order queries

#### `order_fills`

Records individual fills when buy and sell orders are matched.

| Column          | Type        | Description                                       |
|-----------------|-------------|---------------------------------------------------|
| `id`            | UUID        | Primary key, auto-generated                       |
| `buy_order_id`  | UUID        | Foreign key to `orders.id` (buy side)            |
| `sell_order_id` | UUID        | Foreign key to `orders.id` (sell side)           |
| `quantity`      | INT         | Number of units filled in this transaction        |
| `price`         | DOUBLE      | Execution price (usually the maker's price)       |
| `filled_at`     | TIMESTAMPTZ | When the fill occurred                            |

**Constraints:**
- `quantity > 0`
- `price > 0`
- Unique constraint on `(buy_order_id, sell_order_id, filled_at)` to prevent duplicate fills

**Indexes:**
- `idx_order_fills_buy`: Query fills by buy order
- `idx_order_fills_sell`: Query fills by sell order
- `idx_order_fills_time`: Time-based fill queries

## Order Matching Logic

### Price-Time Priority

Orders are matched using a **price-time priority** algorithm:

1. **Buy orders**: Sorted by price (highest first), then by creation time (oldest first)
2. **Sell orders**: Sorted by price (lowest first), then by creation time (oldest first)

### Matching Rules

A trade occurs when:
- A buy order's price ≥ a sell order's price for the same item
- The execution price is typically the **maker's price** (the order that was already in the book)

### Example Scenario

1. Player A places a **sell order** for 10 DIAMOND at 100 coins each
2. Player B places a **buy order** for 5 DIAMOND at 105 coins each
3. **Match occurs**: 
   - Buy price (105) ≥ Sell price (100)
   - 5 DIAMOND traded at 100 coins each (maker's price)
   - Player A's order: `remaining_quantity` reduced to 5
   - Player B's order: `status` changed to 'filled'

### Order Status Transitions

```
open → partially_filled → filled
open → filled
open → cancelled
partially_filled → filled
partially_filled → cancelled
```

## Use Cases

### 1. Placing an Order

```sql
INSERT INTO orders (server_id, item_id, player_id, price, quantity, remaining_quantity, side, status)
VALUES (
    'server-uuid',
    'DIAMOND',
    'player-uuid',
    100.0,
    10,
    10,
    'buy',
    'open'
);
```

### 2. Querying the Order Book

**Get all active buy orders for DIAMOND (highest price first):**
```sql
SELECT * FROM orders
WHERE item_id = 'DIAMOND'
  AND side = 'buy'
  AND status IN ('open', 'partially_filled')
ORDER BY price DESC, created_at ASC;
```

**Get all active sell orders for DIAMOND (lowest price first):**
```sql
SELECT * FROM orders
WHERE item_id = 'DIAMOND'
  AND side = 'sell'
  AND status IN ('open', 'partially_filled')
ORDER BY price ASC, created_at ASC;
```

### 3. Matching Orders

When a new order arrives, find matching orders:
```sql
-- Find sell orders that match a buy order at price 100
SELECT * FROM orders
WHERE item_id = 'DIAMOND'
  AND side = 'sell'
  AND status IN ('open', 'partially_filled')
  AND price <= 100.0
ORDER BY price ASC, created_at ASC
FOR UPDATE;  -- Lock to prevent race conditions
```

### 4. Recording a Fill

```sql
-- Update the buy order
UPDATE orders
SET remaining_quantity = remaining_quantity - 5,
    status = CASE WHEN remaining_quantity - 5 = 0 THEN 'filled' ELSE 'partially_filled' END,
    filled_at = CASE WHEN remaining_quantity - 5 = 0 THEN NOW() ELSE filled_at END
WHERE id = 'buy-order-id';

-- Update the sell order
UPDATE orders
SET remaining_quantity = remaining_quantity - 5,
    status = CASE WHEN remaining_quantity - 5 = 0 THEN 'filled' ELSE 'partially_filled' END,
    filled_at = CASE WHEN remaining_quantity - 5 = 0 THEN NOW() ELSE filled_at END
WHERE id = 'sell-order-id';

-- Record the fill
INSERT INTO order_fills (buy_order_id, sell_order_id, quantity, price)
VALUES ('buy-order-id', 'sell-order-id', 5, 100.0);
```

### 5. Viewing Player Orders

```sql
SELECT * FROM orders
WHERE server_id = 'server-uuid'
  AND player_id = 'player-uuid'
ORDER BY created_at DESC;
```

### 6. Cancelling an Order

```sql
UPDATE orders
SET status = 'cancelled'
WHERE id = 'order-id'
  AND player_id = 'player-uuid'  -- Ensure player owns the order
  AND status IN ('open', 'partially_filled');
```

## Implementation Considerations

### Concurrency Control

- Use `FOR UPDATE` when querying orders for matching to prevent race conditions
- Consider using advisory locks for high-frequency items
- Transaction isolation level should be at least `READ COMMITTED`

### Performance

- Indexes are optimized for order book queries (item + status + price)
- Partial indexes on `status IN ('open', 'partially_filled')` reduce index size
- Consider partitioning `orders` by `item_id` for very high-volume items

### Data Integrity

- Foreign key constraints with `ON DELETE CASCADE` ensure cleanup when servers are deleted
- CHECK constraints prevent invalid states (negative quantities, etc.)
- Unique constraint on fills prevents double-counting

### Future Enhancements

1. **Order expiration**: Add `expires_at` column for time-limited orders
2. **Order types**: Support market orders, stop-loss orders, etc.
3. **Fee tracking**: Add `fee` column to `order_fills` for transaction fees
4. **Volume tracking**: Add materialized view for 24h volume per item
5. **Price impact**: Track how large orders affect the true price

## Migration

The schema is applied via migration `0004_auction_house.sql`. To roll back:

```sql
DROP TABLE IF EXISTS order_fills CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
```

Note: Rolling back will permanently delete all order data.

## Related Systems

- **True Prices**: Orders are placed relative to computed `true_prices`
- **Price History**: Fill prices contribute to `price_history`
- **Servers**: Orders are scoped to individual game servers
