-- Flyway migration V2: PostgreSQL Full-Text Search and Trigram Indexes

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Add search_vector generated column to products table
ALTER TABLE products ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(name, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(brand, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(sku, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(description, '')), 'C')
) STORED;

-- Full-text search GIN index on products search_vector
CREATE INDEX idx_products_search_vector ON products USING GIN (search_vector);

-- Trigram GIN indexes for fast fuzzy/substring search on English and Khmer names
CREATE INDEX idx_products_name_trgm ON products USING GIN (name gin_trgm_ops);
CREATE INDEX idx_products_name_km_trgm ON products USING GIN (name_km gin_trgm_ops);
CREATE INDEX idx_categories_name_trgm ON categories USING GIN (name gin_trgm_ops);
CREATE INDEX idx_categories_name_km_trgm ON categories USING GIN (name_km gin_trgm_ops);
