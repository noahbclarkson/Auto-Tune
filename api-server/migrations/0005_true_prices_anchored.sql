-- Add anchored column: true if this item's price is connected to the anchor item
ALTER TABLE true_prices ADD COLUMN anchored BOOLEAN NOT NULL DEFAULT TRUE;
