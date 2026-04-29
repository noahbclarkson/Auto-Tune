-- Watched auction orders: players can "watch" an order and be notified when it's filled.
-- category: FILLED (order fully filled), PARTIAL (order partially filled — future use)
CREATE TABLE IF NOT EXISTS at_watched_auctions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    order_id TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'FILLED',
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (player_uuid, order_id, category)
);
CREATE INDEX IF NOT EXISTS idx_watched_player ON at_watched_auctions (player_uuid);
CREATE INDEX IF NOT EXISTS idx_watched_order ON at_watched_auctions (order_id);
