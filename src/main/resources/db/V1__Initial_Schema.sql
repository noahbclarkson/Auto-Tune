-- Auto-Tune Database Schema v2
-- Redesigned market engine with time-weighted trades and per-item autosell
-- Supports both SQLite and MySQL/MariaDB

-- Items table: Stores all tradeable items
-- price is the single source of truth - it changes based on trade history
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
    credit_score INTEGER DEFAULT 500,
    total_traded DECIMAL(20, 2) DEFAULT 0,
    total_bought DECIMAL(20, 2) DEFAULT 0,
    total_sold DECIMAL(20, 2) DEFAULT 0,
    transaction_count INTEGER DEFAULT 0,
    first_seen DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_seen DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_players_credit ON at_players(credit_score);

-- Autosell items: Per-item autosell settings for each player
CREATE TABLE IF NOT EXISTS at_autosell_items (
    player_uuid VARCHAR(36) NOT NULL,
    item_id INTEGER NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
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
