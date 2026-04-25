-- Player type column for archetype-aware loan enforcement and targeting.
-- MM = MarketMaker, GB = GuildBuyer, OTHER = regular player
-- Used to enforce guildbuyer_total_debt_cap and block_mm_gb_loans_during_tier3
ALTER TABLE at_player_data ADD COLUMN player_type TEXT NOT NULL DEFAULT 'OTHER';
CREATE INDEX IF NOT EXISTS idx_player_type ON at_player_data (player_type);
