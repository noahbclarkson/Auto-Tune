-- Price ratio data submitted by registered servers
CREATE TABLE IF NOT EXISTS price_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    item_names TEXT[] NOT NULL,
    -- n×n ratio matrix stored as JSONB (array of arrays of floats)
    ratio_matrix_json JSONB NOT NULL,
    player_count INT NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_price_submissions_server_id ON price_submissions(server_id);
CREATE INDEX IF NOT EXISTS idx_price_submissions_submitted_at ON price_submissions(submitted_at DESC);
