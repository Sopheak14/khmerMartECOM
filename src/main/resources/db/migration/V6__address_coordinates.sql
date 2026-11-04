-- Optional map location for delivery addresses (nullable; existing rows unaffected)
ALTER TABLE addresses ADD COLUMN latitude NUMERIC(9,6);
ALTER TABLE addresses ADD COLUMN longitude NUMERIC(9,6);
