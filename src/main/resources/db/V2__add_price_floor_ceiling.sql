-- Add hard price floor and ceiling columns to at_items
-- max_price: absolute upper bound on item price (NULL = no ceiling)
-- min_price: absolute lower bound on item price (NULL = no floor, but PRICE_FLOOR still applies)
ALTER TABLE at_items ADD COLUMN IF NOT EXISTS max_price DECIMAL(20, 2) DEFAULT NULL;
ALTER TABLE at_items ADD COLUMN IF NOT EXISTS min_price DECIMAL(20, 2) DEFAULT NULL;
