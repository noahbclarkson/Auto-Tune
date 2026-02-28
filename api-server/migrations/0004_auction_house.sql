-- Auction house order book system
-- Allows players to place limit orders (buy/sell) at prices between true prices

-- Orders placed by players
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    item_id TEXT NOT NULL,  -- Item identifier (e.g., "DIAMOND", "IRON_INGOT")
    player_id TEXT NOT NULL,  -- Minecraft player UUID or username
    price DOUBLE PRECISION NOT NULL CHECK (price > 0),
    quantity INT NOT NULL CHECK (quantity > 0),
    remaining_quantity INT NOT NULL CHECK (remaining_quantity >= 0),
    side TEXT NOT NULL CHECK (side IN ('buy', 'sell')),
    status TEXT NOT NULL CHECK (status IN ('open', 'filled', 'cancelled', 'partially_filled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    filled_at TIMESTAMPTZ,
    
    -- Ensure remaining_quantity never exceeds original quantity
    CONSTRAINT valid_remaining_quantity CHECK (remaining_quantity <= quantity)
);

-- Indexes for efficient order book queries
CREATE INDEX IF NOT EXISTS idx_orders_item_status ON orders(item_id, status) WHERE status IN ('open', 'partially_filled');
CREATE INDEX IF NOT EXISTS idx_orders_item_price ON orders(item_id, price, side) WHERE status IN ('open', 'partially_filled');
CREATE INDEX IF NOT EXISTS idx_orders_player ON orders(server_id, player_id);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_at DESC);

-- Individual order fills (tracks matching between buy and sell orders)
CREATE TABLE IF NOT EXISTS order_fills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    buy_order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sell_order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    quantity INT NOT NULL CHECK (quantity > 0),
    price DOUBLE PRECISION NOT NULL CHECK (price > 0),
    filled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Prevent duplicate fills for the same order pair
    UNIQUE(buy_order_id, sell_order_id, filled_at)
);

-- Indexes for efficient fill queries
CREATE INDEX IF NOT EXISTS idx_order_fills_buy ON order_fills(buy_order_id);
CREATE INDEX IF NOT EXISTS idx_order_fills_sell ON order_fills(sell_order_id);
CREATE INDEX IF NOT EXISTS idx_order_fills_time ON order_fills(filled_at DESC);
