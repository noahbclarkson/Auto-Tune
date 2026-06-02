-- Auction fill status for atomic economy operations.
-- Allows us to record a fill (source of truth) BEFORE running economy ops
-- and then update to COMPLETED or FAILED based on the economy result.
-- This prevents phantom transactions: DB is written first, then economy
-- ops run, and if they fail we can compensate rather than losing money.
ALTER TABLE at_auction_fills ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED';
CREATE INDEX IF NOT EXISTS idx_auction_fills_status ON at_auction_fills(status);