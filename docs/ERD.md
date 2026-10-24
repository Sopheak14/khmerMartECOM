# KhmerMart Database Entity Relationship Diagram (ERD)

This document visualizes the complete database schema of KhmerMart, mapping entity relationships, cardinalities, primary keys, and foreign keys.

```mermaid
erDiagram
    USERS ||--o{ ADDRESSES : "has many"
    USERS ||--o{ CARTS : "owns"
    USERS ||--o{ ORDERS : "places"
    USERS ||--o{ REVIEWS : "writes"
    USERS ||--o{ WISHLISTS : "saves"
    USERS ||--o{ NOTIFICATIONS : "receives"
    USERS ||--o{ REFRESH_TOKENS : "issues"
    USERS ||--o{ PUSH_SUBSCRIPTIONS : "subscribes"
    USERS ||--o{ AUDIT_LOGS : "acts in"

    CATEGORIES ||--o{ CATEGORIES : "parent-child"
    CATEGORIES ||--o{ PRODUCTS : "contains"

    PRODUCTS ||--o{ PRODUCT_VARIANTS : "has variants"
    PRODUCTS ||--o{ PRODUCT_IMAGES : "has images"
    PRODUCTS ||--o{ INVENTORY : "tracks stock"
    PRODUCTS ||--o{ REVIEWS : "reviewed in"
    PRODUCTS ||--o{ PROMOTION_PRODUCTS : "discounted via"
    PRODUCTS ||--o{ CART_ITEMS : "added to"
    PRODUCTS ||--o{ ORDER_ITEMS : "ordered in"

    PRODUCT_VARIANTS ||--o{ INVENTORY : "variant stock"
    PRODUCT_VARIANTS ||--o{ CART_ITEMS : "selected variant"
    PRODUCT_VARIANTS ||--o{ ORDER_ITEMS : "purchased variant"

    INVENTORY ||--o{ INVENTORY_MOVEMENTS : "records movement"

    CARTS ||--o{ CART_ITEMS : "contains"
    COUPONS ||--o{ COUPON_REDEMPTIONS : "redeemed in"

    PROMOTIONS ||--o{ PROMOTION_PRODUCTS : "applies to"

    ORDERS ||--o{ ORDER_ITEMS : "includes"
    ORDERS ||--o{ ORDER_STATUS_HISTORY : "tracks timeline"
    ORDERS ||--o{ PAYMENTS : "paid via"
    ORDERS ||--o{ SHIPMENTS : "fulfilled by"
    ORDERS ||--o{ COUPON_REDEMPTIONS : "applies coupon"

    SHIPMENTS ||--o{ TRACKING_EVENTS : "emits milestones"

    PAYMENTS ||--o{ REFUNDS : "refunds"
    PAYMENTS ||--o{ BANK_TRANSFER_SLIPS : "slips uploaded"

    USERS {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar phone
        varchar role
        boolean is_active
        boolean email_verified
        timestamp created_at
    }

    PRODUCTS {
        bigint id PK
        varchar sku UK
        varchar slug UK
        varchar name
        varchar name_km
        text description
        text description_km
        bigint price_cents
        bigint category_id FK
        varchar brand
        boolean active
        integer sold_count
        timestamp created_at
    }

    PRODUCT_VARIANTS {
        bigint id PK
        bigint product_id FK
        varchar sku UK
        bigint price_cents
        jsonb attributes
        boolean active
    }

    INVENTORY {
        bigint id PK
        bigint product_id FK
        bigint variant_id FK
        integer quantity_on_hand
        integer quantity_reserved
        integer low_stock_threshold
        boolean low_stock_alerted
    }

    ORDERS {
        bigint id PK
        varchar order_number UK
        bigint user_id FK
        varchar status
        varchar payment_status
        varchar payment_method
        bigint subtotal_cents
        bigint discount_cents
        bigint shipping_cents
        bigint total_cents
        varchar tracking_number UK
        timestamp placed_at
        timestamp delivered_at
    }

    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        bigint variant_id FK
        integer quantity
        bigint unit_price_cents
        bigint line_total_cents
    }

    PAYMENTS {
        bigint id PK
        bigint order_id FK
        varchar method
        varchar status
        bigint amount_cents
        varchar currency
        varchar khqr_md5 UK
        varchar bakong_hash UK
        timestamp expires_at
        timestamp paid_at
    }

    SHIPMENTS {
        bigint id PK
        bigint order_id FK
        varchar tracking_number UK
        varchar carrier
        varchar status
        timestamp shipped_at
    }

    TRACKING_EVENTS {
        bigint id PK
        bigint shipment_id FK
        varchar status
        varchar location
        varchar note
        timestamp created_at
    }

    REVIEWS {
        bigint id PK
        bigint user_id FK
        bigint product_id FK
        integer rating
        varchar title
        text body
        varchar status
        boolean verified_purchase
        text admin_reply
        timestamp created_at
    }

    AUDIT_LOGS {
        bigint id PK
        bigint actor_id FK
        varchar action
        varchar entity
        varchar entity_id
        jsonb before_json
        jsonb after_json
        varchar ip
        timestamp created_at
    }
```
