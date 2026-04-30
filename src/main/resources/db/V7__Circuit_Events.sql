-- Circuit breaker timeline: records transitions between NORMAL, TIER1, TIER2, TIER3, and ADMIN_RECOVERY.
-- Used by bundled web dashboard to explain when economy safeguards engaged or cleared.
CREATE TABLE IF NOT EXISTS at_circuit_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    previous_tier TEXT,
    new_tier TEXT NOT NULL,
    debt_gdp_ratio REAL NOT NULL DEFAULT -1.0,
    gdp DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_debt DECIMAL(19,4) NOT NULL DEFAULT 0,
    interest_multiplier REAL NOT NULL DEFAULT 1.0,
    admin_initiated BOOLEAN NOT NULL DEFAULT FALSE,
    details TEXT,
    timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_circuit_events_timestamp ON at_circuit_events (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_circuit_events_tier ON at_circuit_events (new_tier, timestamp DESC);
