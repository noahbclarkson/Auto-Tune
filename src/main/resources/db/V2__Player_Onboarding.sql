-- V2: Player onboarding milestone tracking
-- Tracks the highest onboarding milestone sent to each player (day 1, 3, 7, 14, 30)
-- so they receive a one-time in-game message series as they progress through their first month.

ALTER TABLE at_players ADD COLUMN last_onboarding_milestone_sent INTEGER DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_players_onboarding ON at_players(last_onboarding_milestone_sent);