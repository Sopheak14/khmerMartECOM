-- Flyway migration V3: Add nullable variant_id to promotion_products for per-variant flash sale pricing

ALTER TABLE promotion_products DROP CONSTRAINT promotion_products_pkey;

ALTER TABLE promotion_products
    ADD COLUMN id BIGSERIAL PRIMARY KEY,
    ADD COLUMN variant_id BIGINT REFERENCES product_variants(id) ON DELETE CASCADE;

ALTER TABLE promotion_products
    ADD CONSTRAINT uq_promotion_products UNIQUE NULLS NOT DISTINCT (promotion_id, product_id, variant_id);

CREATE INDEX idx_promotion_products_variant ON promotion_products(variant_id);
