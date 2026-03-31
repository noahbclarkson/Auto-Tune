-- Auto-Tune Database Schema v1 (consolidated)
-- Redesigned market engine with time-weighted trades and per-item autosell
-- Supports both SQLite and MySQL/MariaDB

-- Items table: Stores all tradeable items
-- price is the single source of truth - it changes based on trade history
-- Note: no maxPrice/minPrice caps - the engine's maxPriceChangePercent, trend
-- dampening, adaptive windows, and sector correlation manage volatility naturally.
CREATE TABLE IF NOT EXISTS at_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    material VARCHAR(64) NOT NULL,
    item_hash VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(128),
    price DECIMAL(20, 2) NOT NULL,
    section VARCHAR(64) DEFAULT 'misc',
    enabled BOOLEAN DEFAULT TRUE,
    buyable BOOLEAN DEFAULT NULL,
    item_data TEXT DEFAULT NULL,
    max_price_change_override DECIMAL(10,2) DEFAULT NULL,
    base_spread_override DECIMAL(10,5) DEFAULT NULL,
    price_floor DECIMAL(20,2) DEFAULT NULL,
    price_ceiling DECIMAL(20,2) DEFAULT NULL,
    price_frozen BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_items_material ON at_items(material);
CREATE INDEX IF NOT EXISTS idx_items_section ON at_items(section);
CREATE INDEX IF NOT EXISTS idx_items_enabled ON at_items(enabled);

-- Market history: Price snapshots for graphing
CREATE TABLE IF NOT EXISTS at_market_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    item_id INTEGER NOT NULL,
    price DECIMAL(20, 2) NOT NULL,
    buy_volume INTEGER DEFAULT 0,
    sell_volume INTEGER DEFAULT 0,
    bpd DECIMAL(10, 5) DEFAULT 0,
    spd DECIMAL(10, 5) DEFAULT 0,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(item_id) REFERENCES at_items(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_history_item ON at_market_history(item_id);
CREATE INDEX IF NOT EXISTS idx_history_timestamp ON at_market_history(timestamp);

-- Players table: Stores player economy data
CREATE TABLE IF NOT EXISTS at_players (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16),
    guild_tag VARCHAR(64) DEFAULT NULL,
    credit_score INTEGER DEFAULT 500,
    total_traded DECIMAL(20, 2) DEFAULT 0,
    total_bought DECIMAL(20, 2) DEFAULT 0,
    total_sold DECIMAL(20, 2) DEFAULT 0,
    transaction_count INTEGER DEFAULT 0,
    first_seen DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_seen DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_defaulted_at DATETIME DEFAULT NULL
);

CREATE INDEX IF NOT EXISTS idx_players_credit ON at_players(credit_score);

-- Autosell items: Per-item autosell settings for each player
CREATE TABLE IF NOT EXISTS at_autosell_items (
    player_uuid VARCHAR(36) NOT NULL,
    item_id INTEGER NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    min_price DECIMAL(20, 2) DEFAULT NULL,
    PRIMARY KEY(player_uuid, item_id),
    FOREIGN KEY(player_uuid) REFERENCES at_players(uuid) ON DELETE CASCADE,
    FOREIGN KEY(item_id) REFERENCES at_items(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_autosell_player ON at_autosell_items(player_uuid);
CREATE INDEX IF NOT EXISTS idx_autosell_item ON at_autosell_items(item_id);

-- Loans table: Active and historical loans
CREATE TABLE IF NOT EXISTS at_loans (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    principal DECIMAL(20, 2) NOT NULL,
    current_balance DECIMAL(20, 2) NOT NULL,
    interest_rate DECIMAL(5, 4) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    due_date DATETIME NOT NULL,
    last_interest_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    FOREIGN KEY(player_uuid) REFERENCES at_players(uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_loans_player ON at_loans(player_uuid);
CREATE INDEX IF NOT EXISTS idx_loans_status ON at_loans(status);
CREATE INDEX IF NOT EXISTS idx_loans_due ON at_loans(due_date);

-- Item price ratios: Stores price relationships between items for cross-server inference
-- ratio = item_a_price / item_b_price (item_a < item_b for normalization)
CREATE TABLE IF NOT EXISTS at_item_ratios (
    item_a INTEGER NOT NULL,
    item_b INTEGER NOT NULL,
    ratio DECIMAL(20, 10) NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(item_a, item_b),
    FOREIGN KEY(item_a) REFERENCES at_items(id) ON DELETE CASCADE,
    FOREIGN KEY(item_b) REFERENCES at_items(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ratios_item_a ON at_item_ratios(item_a);
CREATE INDEX IF NOT EXISTS idx_ratios_item_b ON at_item_ratios(item_b);

-- Transactions: Audit log of all trades (used for price calculations)
CREATE TABLE IF NOT EXISTS at_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    item_id INTEGER NOT NULL,
    transaction_type VARCHAR(8) NOT NULL,
    amount INTEGER NOT NULL,
    price_per_unit DECIMAL(20, 2) NOT NULL,
    total_price DECIMAL(20, 2) NOT NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(player_uuid) REFERENCES at_players(uuid) ON DELETE CASCADE,
    FOREIGN KEY(item_id) REFERENCES at_items(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transactions_player ON at_transactions(player_uuid);
CREATE INDEX IF NOT EXISTS idx_transactions_item ON at_transactions(item_id);
CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON at_transactions(timestamp);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON at_transactions(transaction_type);

-- Sections: Item categories for shop organization
CREATE TABLE IF NOT EXISTS at_sections (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    icon VARCHAR(64) DEFAULT 'CHEST',
    priority INTEGER DEFAULT 0
);

INSERT OR IGNORE INTO at_sections (id, display_name, icon, priority) VALUES
    ('blocks', 'Building Blocks', 'BRICKS', 10),
    ('ores', 'Ores & Minerals', 'DIAMOND_ORE', 20),
    ('tools', 'Tools & Equipment', 'DIAMOND_PICKAXE', 30),
    ('weapons', 'Weapons & Combat', 'DIAMOND_SWORD', 40),
    ('armor', 'Armor', 'DIAMOND_CHESTPLATE', 50),
    ('food', 'Food & Farming', 'GOLDEN_APPLE', 60),
    ('potions', 'Potions & Brewing', 'BREWING_STAND', 70),
    ('redstone', 'Redstone & Mechanics', 'REDSTONE', 80),
    ('misc', 'Miscellaneous', 'CHEST', 100);

-- Economy snapshots: GDP, debt, inflation tracking
CREATE TABLE IF NOT EXISTS at_economy_snapshots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    gdp DECIMAL(20, 2) NOT NULL,
    total_debt DECIMAL(20, 2) NOT NULL,
    active_loans INTEGER NOT NULL,
    player_count INTEGER NOT NULL,
    average_price_change DECIMAL(10, 5) NOT NULL,
    transaction_volume DECIMAL(20, 2) NOT NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_economy_snapshots_timestamp ON at_economy_snapshots(timestamp);

-- Market events: scheduled or active server-wide market events that modify price behavior
CREATE TABLE IF NOT EXISTS at_market_events (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    materials TEXT DEFAULT NULL,
    price_multiplier DECIMAL(10, 5) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    start_message TEXT DEFAULT NULL,
    end_message TEXT DEFAULT NULL,
    created_by VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    cron_expression VARCHAR(64) DEFAULT NULL,
    tick_count INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_events_status ON at_market_events(status);
CREATE INDEX IF NOT EXISTS idx_events_starts_at ON at_market_events(starts_at);
CREATE INDEX IF NOT EXISTS idx_events_ends_at ON at_market_events(ends_at);

-- Treasury: server-wide tax accumulator
-- Tax config in config.yml controls which transactions are taxed and at what rate.
-- The at_treasury table holds the accumulated balance.
CREATE TABLE IF NOT EXISTS at_treasury (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    balance DECIMAL(20, 2) NOT NULL DEFAULT 0,
    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Price alerts: player notifications when an item's price crosses a threshold
CREATE TABLE IF NOT EXISTS at_price_alerts (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    item_id INTEGER NOT NULL,
    alert_type VARCHAR(16) NOT NULL,
    target_price DECIMAL(20, 2) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    triggered_at DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(player_uuid) REFERENCES at_players(uuid) ON DELETE CASCADE,
    FOREIGN KEY(item_id) REFERENCES at_items(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_alerts_player ON at_price_alerts(player_uuid);
CREATE INDEX IF NOT EXISTS idx_alerts_item ON at_price_alerts(item_id);
CREATE INDEX IF NOT EXISTS idx_alerts_enabled ON at_price_alerts(enabled);

-- Price overrides: Admin-set manual prices that bypass the market engine.
-- NULL expires_at means the override is permanent.
CREATE TABLE IF NOT EXISTS at_price_overrides (
    item_id INTEGER PRIMARY KEY,
    price DECIMAL(20, 2) NOT NULL,
    expires_at DATETIME DEFAULT NULL,
    set_by VARCHAR(36) DEFAULT NULL,
    set_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(item_id) REFERENCES at_items(id) ON DELETE CASCADE
);

-- Auction orders: Persistent limit order book for in-game player-to-player trading.
-- Price-time priority matching (highest buy / lowest sell first).
CREATE TABLE IF NOT EXISTS at_auction_orders (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    item_data TEXT DEFAULT NULL,
    price DECIMAL(20, 2) NOT NULL,
    original_quantity INTEGER NOT NULL,
    remaining_quantity INTEGER NOT NULL,
    side VARCHAR(4) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    filled_at DATETIME DEFAULT NULL,
    expires_at DATETIME NOT NULL,
    FOREIGN KEY(player_uuid) REFERENCES at_players(uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_auction_orders_player ON at_auction_orders(player_uuid);
CREATE INDEX IF NOT EXISTS idx_auction_orders_material ON at_auction_orders(material);
CREATE INDEX IF NOT EXISTS idx_auction_orders_side ON at_auction_orders(side);
CREATE INDEX IF NOT EXISTS idx_auction_orders_status ON at_auction_orders(status);

-- Auction fills: Completed matches between buy and sell orders.
CREATE TABLE IF NOT EXISTS at_auction_fills (
    id VARCHAR(36) PRIMARY KEY,
    buy_order_id VARCHAR(36) NOT NULL,
    sell_order_id VARCHAR(36) NOT NULL,
    quantity INTEGER NOT NULL,
    price DECIMAL(20, 2) NOT NULL,
    filled_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(buy_order_id) REFERENCES at_auction_orders(id) ON DELETE CASCADE,
    FOREIGN KEY(sell_order_id) REFERENCES at_auction_orders(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_auction_fills_buy ON at_auction_fills(buy_order_id);
CREATE INDEX IF NOT EXISTS idx_auction_fills_sell ON at_auction_fills(sell_order_id);

-- Price alerts: Players set price thresholds and get notified when the market price crosses them.
-- alert_type: ABOVE = notify when price rises above target, BELOW = notify when price falls below target
-- triggered_at: NULL means alert is active; set when triggered so it only fires once per crossing.
-- Players can re-arm triggered alerts or remove them.
CREATE TABLE IF NOT EXISTS at_price_alerts (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    item_id INTEGER NOT NULL,
    alert_type VARCHAR(8) NOT NULL,
    target_price DECIMAL(20, 2) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    triggered_at DATETIME DEFAULT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    FOREIGN KEY(player_uuid) REFERENCES at_players(uuid) ON DELETE CASCADE,
    FOREIGN KEY(item_id) REFERENCES at_items(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_alerts_player ON at_price_alerts(player_uuid);
CREATE INDEX IF NOT EXISTS idx_alerts_item ON at_price_alerts(item_id);
CREATE INDEX IF NOT EXISTS idx_alerts_active ON at_price_alerts(enabled) WHERE enabled = TRUE;

-- Player achievement badges: Tracks badges earned by players.
-- Badges are one-time achievements earned through market activity.
-- Criteria are evaluated on-demand; once earned the badge is persisted here.
CREATE TABLE IF NOT EXISTS at_player_badges (
    player_uuid VARCHAR(36) NOT NULL,
    badge_type VARCHAR(32) NOT NULL,
    earned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(player_uuid, badge_type),
    FOREIGN KEY(player_uuid) REFERENCES at_players(uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_badges_player ON at_player_badges(player_uuid);
CREATE INDEX IF NOT EXISTS idx_badges_type ON at_player_badges(badge_type);
