-- Create the 'players' table
CREATE TABLE IF NOT EXISTS players (
    player_uuid TEXT PRIMARY KEY
);

-- Create the 'sections' table
CREATE TABLE IF NOT EXISTS sections (
    section_id INTEGER PRIMARY KEY AUTOINCREMENT,
    item TEXT,
    back_enabled BOOLEAN,
    pos_x INT,
    pos_y INT
);

-- Create the 'shops' table
CREATE TABLE IF NOT EXISTS shops (
    shop_id INTEGER PRIMARY KEY AUTOINCREMENT,
    item TEXT,
    is_locked BOOLEAN,
    enchantment BOOLEAN,
    custom_spd REAL NULL,
    custom_volatility REAL NULL,
    max_buys INT NULL,
    max_sells INT NULL,
    custom_update_rate INT NULL,
    section_id INT,
    FOREIGN KEY (section_id) REFERENCES sections(section_id)
);

-- Create the 'price_history' table
CREATE TABLE IF NOT EXISTS price_history (
    price_history_id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INT,
    date_effective TEXT,
    price REAL,
    FOREIGN KEY (shop_id) REFERENCES shops(shop_id)
);

-- Create the 'transactions' table
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INT,
    transaction_date TEXT,
    amount REAL,
    position BOOLEAN,
    player_uuid TEXT,
    FOREIGN KEY (shop_id) REFERENCES shops(shop_id),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

-- Create the 'autosell' table
CREATE TABLE IF NOT EXISTS autosell (
    autosell_id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INT,
    player_uuid TEXT,
    autosell_enabled BOOLEAN,
    FOREIGN KEY (shop_id) REFERENCES shops(shop_id),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

-- Create the 'economy_history' table
CREATE TABLE IF NOT EXISTS economy_history (
    economy_id INTEGER PRIMARY KEY AUTOINCREMENT,
    date_effective TEXT,
    gdp REAL,
    balance REAL,
    population INT,
    loss REAL,
    debt REAL,
    inflation REAL
);
