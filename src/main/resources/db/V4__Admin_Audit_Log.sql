-- Admin economy audit log: timestamped record of all admin actions
-- affecting economy state (freezes, price overrides, floor/ceiling, config changes, events)
CREATE TABLE IF NOT EXISTS at_admin_audit_log (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    admin_uuid   TEXT,                              -- may be null for console
    admin_name   TEXT NOT NULL,
    action_type  TEXT NOT NULL,                     -- MARKET_FREEZE, PRICE_SET, ITEM_FLOOR, CONFIG_RELOAD, etc.
    target       TEXT,                               -- material name, event name, config key, etc.
    old_value    TEXT,                               -- previous value (null for creates)
    new_value    TEXT,                               -- new value (null for deletes)
    details      TEXT                                -- human-readable description
);

CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON at_admin_audit_log (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action   ON at_admin_audit_log (action_type, timestamp DESC);
