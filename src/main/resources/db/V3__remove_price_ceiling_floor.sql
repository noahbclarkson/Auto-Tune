-- Remove per-item absolute price ceiling/floor columns.
-- These defeated the purpose of a dynamic supply-and-demand economy.
ALTER TABLE at_items DROP COLUMN IF EXISTS max_price;
ALTER TABLE at_items DROP COLUMN IF EXISTS min_price;
