-- V3: Player trading streaks
-- Tracks consecutive days of trading activity per player.
-- A "streak day" is any UTC day where the player completes at least one transaction (buy or sell).
-- The streak counter resets to 0 if a day passes with no trading activity.

CREATE TABLE IF NOT EXISTS at_player_streaks (
    player_uuid VARCHAR(36) PRIMARY KEY,
    current_streak INTEGER NOT NULL DEFAULT 0,
    best_streak INTEGER NOT NULL DEFAULT 0,
    last_trade_date DATE NOT NULL,
    FOREIGN KEY(player_uuid) REFERENCES at_players(uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_streaks_current ON at_player_streaks(current_streak DESC);
CREATE INDEX IF NOT EXISTS idx_streaks_best ON at_player_streaks(best_streak DESC);
