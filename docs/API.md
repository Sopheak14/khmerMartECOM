# KhmerMart Backend API Specification & Behavior

## 1. Product Search & Faceting Architecture

### Disjunctive Faceting Behavior
In the KhmerMart Catalog API (`GET /api/products`), facet calculations are **disjunctive** with respect to user-selected filters:
- **Disjunctive Filtering**: Each facet calculates counts based on the base search query (`q`) and category tree hierarchy (`categoryId`), but **ignores its own facet filter constraints** (such as selected brand, attribute filters, or price range).
- **Rationale**: This allows users to see the available counts for alternative values within the same facet (e.g. seeing how many laptops exist for Asus and Apple even when filtering by Dell), preventing facets from collapsing to a single selected value with count 1.
- **Combined Scope**:
  - `brands`: Lists all brands with active product counts in the current search query and category scope.
  - `attributes`: Aggregates all variant attributes (`ram`, `color`, `storage`, `size`, `capacity`, `weight`) for products in the current search/category scope.
  - `priceRange`: Reports the minimum (`minCents`) and maximum (`maxCents`) price among all products matching the search query and category scope.

---

## 2. Stock & Inventory Rules
- **Products With Variants**:
  - `product.available` is strictly computed as `SUM(variant.available)` across all active variants for that product.
  - A product without active variants uses its product-level inventory record.
  - This count is guaranteed to be consistent across `/api/products` (card view), `/api/home` (curated feeds), and `/api/products/{idOrSlug}` (detail view).

---

## 3. Product Badges Rules
Badges are dynamically determined:
- `POPULAR`: Awarded only to the top 8 products ranked by `soldCount`.
- `NEW`: Awarded to products created within the last 14 days (`createdAt >= now - 14 days`).
- `FLASH_SALE`: Awarded only to products actively participating in an active `FLASH_SALE` promotion.
- `LOW_STOCK`: Awarded when total available inventory is `<= 5`.
- `BOGO`: Awarded to products participating in a `BUY1GET1` (`BOGO`) promotion.

---

## 4. Cart Pricing & Promotion Rules
Pricing calculations (`PricingServiceImpl`) strictly follow these promotional and stacking rules:
- **At Most One Automatic Promotion Per Cart Line**: Each cart line receives at most one automatic promotion, choosing the single best discount among `FLASH_SALE`, `SEASONAL %`, or `BOGO` for that item. Multiple automatic promotions never stack onto the same line (e.g. seasonal % is not computed on top of a flash sale price).
- **Targeted Promotions**: `SEASONAL %` applies strictly to products linked in `promotion_products` (never the entire store). Flash sales apply only to products with an explicit `sale_price_cents`.
- **Coupon Stacking**: At most one coupon can be applied to an order. The coupon discount is computed on the remaining subtotal **AFTER** all automatic promotions have been deducted.
- **Totals Non-Negative**: All totals, discount calculations, and final amounts are floored at 0 cents (totals are never below 0).
- **Shipping Waiver**: Standard shipping fee is waived when subtotal >= `freeOverCents` threshold (e.g., $50) or when an active `FREESHIP` coupon is applied.


---

## 5. Notifications, Tracking & Review Rules

### 5.1 Notifications Architecture
- **Event-Driven Decoupling**: Domain events are published across order, payment, and inventory lifecycles (`OrderPlacedEvent`, `PaymentPaidEvent`, `OrderShippedEvent`, `OrderOutForDeliveryEvent`, `OrderDeliveredEvent`, `OrderCancelledEvent`, `InventoryLowEvent`, `ReviewRepliedEvent`, `PasswordChangedEvent`).
- **Transactional Consistency**: Listeners are annotated with `@TransactionalEventListener(phase = AFTER_COMMIT)`. If a database transaction rolls back (e.g. during failed checkout), no notification, email, or SMS is ever dispatched.
- **Audit Logging**: Every dispatched notification across channels (`IN_APP`, `EMAIL`, `SMS`, `PUSH`) creates a durable record in the `notifications` table with status tracking (`SENT`, `DELIVERED`, `READ`).
- **Bilingual Email Templates**: All email templates are authored with Thymeleaf, containing dual English and Khmer (`ខ្មែរ`) headings, clear line item breakdowns, and direct action links to `${FRONTEND_URL}`.

### 5.2 Tracking & Carrier State Machine
- **Tracking Identifiers**: Auto-generated tracking numbers follow the format `KMTRK` + 10 digits (e.g. `KMTRK7518192639`).
- **Strict Transition Enforcement**: Shipment events strictly follow: `PREPARING` -> `DISPATCHED` -> `IN_TRANSIT` -> `OUT_FOR_DELIVERY` -> `DELIVERED`. Attempting an illegal skip (e.g. from `DISPATCHED` straight to `DELIVERED`) throws HTTP 422 `INVALID_STATUS_TRANSITION`.
- **Cash on Delivery (COD) Automation**: When a COD order's shipment status reaches `DELIVERED`, the order payment status automatically transitions to `PAID` with recorded `paidAt` timestamp.

### 5.3 Review Verification & Ratings Engine
- **Verified Purchaser Requirement**: Reviews can only be submitted (`POST /api/products/{id}/reviews`) by users who have completed a `DELIVERED` order containing that product. Unverified purchasers receive HTTP 403 `FORBIDDEN`.
- **Single Review Constraint**: A user may submit at most one review per product. Subsequent review attempts return HTTP 409 `CONFLICT` directing the user to edit via `PATCH /api/reviews/{id}`.
- **canReview Flag**: Order detail responses (`GET /api/orders/{orderNumber}`) calculate `items[].canReview: true` strictly when the order is `DELIVERED` AND the user has not yet reviewed that product.
- **Rating Computation**: Product ratings (`avgRating` and `reviewCount`) are computed strictly from `APPROVED` reviews whenever an admin moderates or approves a review.
