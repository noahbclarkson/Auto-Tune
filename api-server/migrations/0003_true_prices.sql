-- Computed true prices derived from all server submissions
CREATE TABLE IF NOT EXISTS true_prices (
    item_name TEXT PRIMARY KEY,
    price DOUBLE PRECISION NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,  -- 0.0 to 1.0
    server_count INT NOT NULL DEFAULT 0,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Price history: snapshots of true_prices over time
CREATE TABLE IF NOT EXISTS price_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_name TEXT NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    server_count INT NOT NULL,
    snapshot_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_price_history_item ON price_history(item_name, snapshot_at DESC);
