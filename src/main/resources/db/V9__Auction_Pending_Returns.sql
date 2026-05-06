-- Auction pending item returns: tracks items that could not be delivered to a
-- buyer's inventory during a fill (e.g., inventory full) or an expired sell
-- order return (player offline).
--
-- Players reclaim via /auction reclaim. On login, PlayerListener checks for
-- pending returns and delivers them automatically.
CREATE TABLE IF NOT EXISTS at_auction_pending_returns (
    id          VARCHAR(36)  PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    fill_id     VARCHAR(36),           -- nullable; not all sources have a fill ref
    material    VARCHAR(64) NOT NULL,
    item_data   TEXT,                  -- Base64-serialized ItemStack (enchantments, etc.)
    quantity    INTEGER  NOT NULL,
    reason      TEXT     NOT NULL,     -- 'INVENTORY_FULL', 'OFFLINE_BUYER', 'EXPIRED_ORDER'
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    returned_at DATETIME,
    FOREIGN KEY (player_uuid) REFERENCES at_players(uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pending_returns_player
    ON at_auction_pending_returns(player_uuid)
    WHERE returned_at IS NULL;
