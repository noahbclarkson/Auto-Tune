-- Per-server exchange rate history.
-- Computed as the geometric mean of log-ratios between the server's submitted ratio
-- matrix and the current true prices anchored at the server's most recent submission.
CREATE TABLE IF NOT EXISTS server_exchange_rate_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    rate DOUBLE PRECISION NOT NULL,
    player_count INT NOT NULL DEFAULT 0,
    snapshot_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_server_exchange_rate_history_server_id
    ON server_exchange_rate_history(server_id, snapshot_at DESC);