-- KhmerMart V1 Database Schema

-- Users & Auth
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id VARCHAR(100) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_id BIGINT
);

CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

-- Addresses & Provinces
CREATE TABLE provinces (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    name_km VARCHAR(255) NOT NULL
);

CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(100) NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    district VARCHAR(100) NOT NULL,
    province VARCHAR(100) NOT NULL,
    postal_code VARCHAR(50),
    is_default BOOLEAN NOT NULL DEFAULT false
);

-- Categories & Catalog
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    name_km VARCHAR(255),
    slug VARCHAR(255) NOT NULL UNIQUE,
    parent_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    image_url VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    name_km VARCHAR(255),
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    brand VARCHAR(100),
    price_cents BIGINT NOT NULL,
    compare_at_cents BIGINT,
    category_id BIGINT REFERENCES categories(id) ON DELETE RESTRICT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    avg_rating NUMERIC(2,1) NOT NULL DEFAULT 0.0,
    review_count INT NOT NULL DEFAULT 0,
    sold_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    price_cents BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT true
);

-- Inventory
CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    variant_id BIGINT REFERENCES product_variants(id) ON DELETE CASCADE,
    quantity_on_hand INT NOT NULL DEFAULT 0,
    quantity_reserved INT NOT NULL DEFAULT 0,
    low_stock_threshold INT NOT NULL DEFAULT 5,
    low_stock_alerted BOOLEAN NOT NULL DEFAULT false,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_inventory_reserved CHECK (quantity_reserved >= 0 AND quantity_on_hand >= quantity_reserved)
);

CREATE UNIQUE INDEX idx_inventory_prod_var ON inventory (product_id, COALESCE(variant_id, 0));

CREATE TABLE inventory_movements (
    id BIGSERIAL PRIMARY KEY,
    inventory_id BIGINT NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    reason VARCHAR(255),
    ref_order_id BIGINT,
    actor_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL
);

-- Carts
CREATE TABLE carts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    guest_token VARCHAR(100) UNIQUE,
    coupon_id BIGINT,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    variant_id BIGINT REFERENCES product_variants(id) ON DELETE CASCADE,
    quantity INT NOT NULL,
    unit_price_cents_snapshot BIGINT NOT NULL
);

CREATE UNIQUE INDEX idx_cart_items_unique ON cart_items (cart_id, product_id, COALESCE(variant_id, 0));

-- Coupons & Promotions
CREATE TABLE coupons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    value BIGINT NOT NULL,
    description TEXT,
    min_order_cents BIGINT,
    max_discount_cents BIGINT,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    usage_limit INT,
    per_user_limit INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    applies_to_category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    applies_to_product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE coupon_redemptions (
    id BIGSERIAL PRIMARY KEY,
    coupon_id BIGINT NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE promotions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    name_km VARCHAR(255),
    type VARCHAR(50) NOT NULL,
    discount_percent INT,
    threshold_cents BIGINT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    banner_image VARCHAR(500),
    link VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE promotion_products (
    promotion_id BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sale_price_cents BIGINT,
    PRIMARY KEY (promotion_id, product_id)
);

-- Shipping
CREATE TABLE shipping_methods (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    name_km VARCHAR(255) NOT NULL,
    fee_cents BIGINT NOT NULL,
    free_over_cents BIGINT,
    eta_days_min INT NOT NULL,
    eta_days_max INT NOT NULL,
    phnom_penh_only BOOLEAN NOT NULL DEFAULT false
);

-- Orders
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(50) NOT NULL,
    payment_status VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    subtotal_cents BIGINT NOT NULL,
    discount_cents BIGINT NOT NULL DEFAULT 0,
    shipping_cents BIGINT NOT NULL DEFAULT 0,
    total_cents BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    coupon_code VARCHAR(100),
    shipping_address JSONB NOT NULL,
    shipping_method_code VARCHAR(50) NOT NULL,
    tracking_number VARCHAR(100) UNIQUE,
    carrier VARCHAR(100),
    notes TEXT,
    payment_deadline TIMESTAMPTZ,
    placed_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    shipped_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancel_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    variant_id BIGINT REFERENCES product_variants(id) ON DELETE SET NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    sku_snapshot VARCHAR(100) NOT NULL,
    attributes_snapshot JSONB,
    image_url_snapshot VARCHAR(500),
    unit_price_cents BIGINT NOT NULL,
    quantity INT NOT NULL,
    line_total_cents BIGINT NOT NULL
);

CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    note TEXT,
    actor_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL
);

-- Payments
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    method VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    amount_khr BIGINT,
    khqr_string TEXT,
    khqr_md5 VARCHAR(100) UNIQUE,
    qr_expires_at TIMESTAMPTZ,
    last_checked_at TIMESTAMPTZ,
    check_count INT NOT NULL DEFAULT 0,
    bakong_hash VARCHAR(255) UNIQUE,
    from_account_id VARCHAR(100),
    to_account_id VARCHAR(100),
    external_ref VARCHAR(100),
    failure_reason VARCHAR(255),
    paid_at TIMESTAMPTZ,
    raw_response JSONB,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE bank_transfer_slips (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    file_url VARCHAR(500) NOT NULL,
    status VARCHAR(50) NOT NULL,
    reviewed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ
);

CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    amount_cents BIGINT NOT NULL,
    reason VARCHAR(255),
    reference VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    actor_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL
);

-- Shipments & Tracking
CREATE TABLE shipments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    carrier VARCHAR(100) NOT NULL,
    tracking_number VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    estimated_delivery TIMESTAMPTZ
);

CREATE TABLE tracking_events (
    id BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    location VARCHAR(255),
    note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);

-- Reviews
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_item_id BIGINT REFERENCES order_items(id) ON DELETE SET NULL,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(255),
    body TEXT,
    status VARCHAR(50) NOT NULL,
    admin_reply TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_review_user_prod UNIQUE (user_id, product_id)
);

-- Notifications & Push
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(50) NOT NULL,
    type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    link VARCHAR(255),
    payload JSONB,
    status VARCHAR(50) NOT NULL,
    read_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint TEXT NOT NULL UNIQUE,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Wishlist & Audit & Settings & Idempotency
CREATE TABLE wishlists (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, product_id)
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT,
    action VARCHAR(100) NOT NULL,
    entity VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    before_json JSONB,
    after_json JSONB,
    ip VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE settings (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE idempotency_records (
    key VARCHAR(255) PRIMARY KEY,
    user_id BIGINT,
    response_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Performance Indexes
CREATE INDEX idx_products_slug ON products (slug);
CREATE INDEX idx_products_category_active ON products (category_id, is_active);
CREATE INDEX idx_products_price ON products (price_cents);
CREATE INDEX idx_orders_user_status_placed ON orders (user_id, status, placed_at);
CREATE INDEX idx_payments_khqr_md5 ON payments (khqr_md5);
CREATE INDEX idx_payments_status_expires ON payments (status, qr_expires_at);
CREATE INDEX idx_reviews_product_status ON reviews (product_id, status);
CREATE INDEX idx_notifications_user_read ON notifications (user_id, read_at);
