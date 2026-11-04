# KhmerMart Complete API Contract Specification

This document details the complete REST API contract for KhmerMart, covering all public endpoints and the Section H (Admin Backoffice) contract.

---

## 1. Authentication & Users
- `POST /api/auth/register`: Create customer account.
- `POST /api/auth/login`: Issue access token + `km_rt` cookie.
- `POST /api/auth/refresh`: Refresh expired access token using rotating cookie.
- `POST /api/auth/logout`: Revoke active refresh token.
- `GET /api/users/me`: Current user profile.
- `PATCH /api/users/me`: Update profile (name, phone).
- `PATCH /api/users/me/password`: Change account password (invalidates sessions).
- `GET /api/users/me/addresses`: List user delivery addresses.
- `POST /api/users/me/addresses`: Create new delivery address.
- `GET /api/users/me/addresses/{id}`: Get one delivery address.
- `PATCH /api/users/me/addresses/{id}`: Partially update delivery address.
- `DELETE /api/users/me/addresses/{id}`: Delete delivery address.

  Address fields: `postalCode` is fully optional (nullable). `latitude` / `longitude` are optional additive `NUMERIC(9,6)` fields on `AddressDto` and the create/update requests; they must be sent together and lie inside Cambodia (lat 9.5..15.0, lng 102.0..108.0), otherwise `VALIDATION_ERROR`. On update, omitted coordinates keep their stored value. The admin order detail `shippingAddress` includes them so staff can open a map link (e.g. `https://www.google.com/maps?q=<lat>,<lng>`). Pre-existing addresses and order snapshots have no coordinates (fields omitted).

---

## 2. Catalog & Discovery
- `GET /api/home`: Curated home feeds (featured, flash sale, new arrivals, popular).
- `GET /api/categories`: Category tree with subcategories and product counts.
- `GET /api/products`: Full-text search, multi-faceted filtering, sorting, pagination. **`minPrice` / `maxPrice` are WHOLE US DOLLARS** (e.g. `minPrice=8&maxPrice=20` = $8–$20); there is no cents form. Response prices (`priceCents`, …) stay in cents.
- `GET /api/products/{idOrSlug}`: Full product detail with variants and active promotions.
- `GET /api/search/suggest`: Fast typeahead autocomplete suggestions.

### 2.1 Pricing & Promotion Contract
- **`VariantDTO`**:
  - `id`: `Long`
  - `sku`: `String`
  - `attributes`: `Map<String, String>`
  - `priceCents`: `Long`
  - `available`: `int`
  - `salePriceCents`: `Long` (additive, optional; present only when that specific variant is on an active flash sale; omitted when not on sale).
- **`ProductCardDTO` & `ProductDetailDTO`**:
  - `salePriceCents`: `Long` (lowest active variant sale price, or product sale price when the product has no variants).


---

## 3. Cart & Checkout
- `GET /api/cart`: Current shopping cart with live price calculations.
- `POST /api/cart/items`: Add item to cart.
- `PATCH /api/cart/items/{id}`: Change quantity of a cart line.
- `DELETE /api/cart/items/{id}`: Remove item from cart.
- `DELETE /api/cart`: Clear the whole cart.
- `POST /api/cart/coupon`: Apply promotional coupon code.
- `DELETE /api/cart/coupon`: Remove applied coupon code.
- `POST /api/checkout/preview`: Price/shipping preview. `availablePaymentMethods` lists only the currently enabled methods (`KHQR`, `COD`).
- `POST /api/checkout`: Idempotent order checkout with stock reservation (`Idempotency-Key`). `paymentMethod` must be one of the enabled methods (`KHQR`, `COD`); `BANK_TRANSFER`/`CARD`/`WALLET` → `422 PAYMENT_METHOD_NOT_AVAILABLE`.

---

## 4. Orders & Public Payments
- `GET /api/orders`: List customer purchase history.
- `GET /api/orders/{orderNumber}`: Customer order detail and tracking timeline. `OrderDTO.discountBreakdown` = `{ "promotions": [{ "label", "amountCents" }], "coupon": { "code", "amountCents" } | null }`, read from the checkout snapshot (never recalculated); `discountCents` = sum(promotions) + coupon. Orders without a snapshot return `promotions: []`, `coupon: null`. Also returned by `POST /api/checkout` and the admin order endpoints.
- `POST /api/orders/{orderNumber}/cancel`: Customer order cancellation.
- `GET /api/orders/{orderNumber}/invoice.pdf`: Download PDF tax invoice.
- `POST /api/payments/khqr/{orderNumber}`: Generate (or return the active) KHQR payment for a `PENDING_PAYMENT` order. Returns `paymentId`, `qrString`, `qrImageDataUrl`, `expiresAt`, `streamToken`. The order's `paymentDeadline` **equals** this `expiresAt`.
- `POST /api/payments/khqr/{orderNumber}/regenerate`: Expire previous pending QR and issue a fresh one.
- `GET /api/payments/{paymentId}/status`: Current payment + order status. Owner or STAFF/ADMIN only; any other user gets `404` (existence is not revealed); unauthenticated gets `401`.
- `GET /api/payments/{paymentId}/stream`: Server-Sent Events status stream. Authorised by the owner's `?token=<streamToken>` (EventSource cannot send headers) or by a STAFF/ADMIN bearer token. Missing/invalid token → `401`; token/user not owning the payment → `404`.
- `POST /api/payments/{paymentId}/simulate-paid`: MOCK-mode settlement simulator. Only valid for a `PENDING` payment (idempotent for `PAID`); `EXPIRED`/`FAILED` → `422 INVALID_STATUS_TRANSITION`.
- `POST /api/payments/bank-transfer/{orderNumber}/slip`: Upload bank transfer deposit slip (multipart `file`).

---

## 5. Shipping & Public Tracking
- `GET /api/shipping/methods`: Available shipping methods and rates.
- `GET /api/track/{trackingNumber}`: Public shipment milestone tracking.

---

## 6. Reviews & Notifications
- `GET /api/products/{productId}/reviews`: Approved reviews and rating histogram.
- `POST /api/products/{productId}/reviews`: Submit customer review (verified purchase).
- `PATCH /api/reviews/{id}`: Edit customer review.
- `GET /api/notifications`: Customer in-app notification inbox.
- `GET /api/notifications/unread-count`: Returns `{"count": N}`.
- `PATCH /api/notifications/{id}/read`: Mark notification as read.
- `PATCH /api/notifications/read-all`: Mark all notifications as read.
- `GET /api/push/vapid-public-key`: Returns `{"key": "..."}`.
- `POST /api/push/subscribe`: Register WebPush browser subscription.

---

## 7. Section H: Backoffice Administration (Admin API)

### 7.1 Dashboard
- `GET /api/admin/dashboard`: (query `range=today|7d|30d`) Comprehensive executive KPI dashboard matching [`DashboardDto`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/model/response/DashboardDto.java).

### 7.2 Users Management
- `GET /api/admin/users`: Search, filter by role/status, paginate users.
- `PATCH /api/admin/users/{id}`: Change role (`CUSTOMER`, `STAFF`, `ADMIN`) or activate/deactivate account (revoking all refresh tokens).

### 7.3 Products & Catalog
- `GET /api/admin/products`: Admin catalog table with stock and pricing.
- `POST /api/admin/products`: Create product with variants, attributes, and Khmer name (`nameKm`).
- `GET /api/admin/products/{id}`: Full backoffice product detail.
- `PUT /api/admin/products/{id}`: Update product information and variants.
- `DELETE /api/admin/products/{id}`: Delete product (soft-deletes if historical orders exist).
- `POST /api/admin/products/{id}/images`: Upload product image with binary magic bytes validation.
- `PATCH /api/admin/products/{id}/images/order`: Reorder product images.
- `GET /api/admin/products/export.csv`: Export product catalog as CSV.
- `POST /api/admin/products/import`: Batch import products from CSV with per-row error tracking.

### 7.4 Categories
- `GET /api/admin/categories`: List category hierarchy.
- `POST /api/admin/categories`: Create category.
- `PUT /api/admin/categories/{id}`: Update category.
- `DELETE /api/admin/categories/{id}`: Delete category (rejects with 409 if category has products).

### 7.5 Inventory
- `GET /api/admin/inventory`: Stock listing with `lowStock=true` filter.
- `POST /api/admin/inventory/{id}/restock`: Restock quantity (resets `lowStockAlerted` flag).
- `POST /api/admin/inventory/{id}/adjust`: Audit adjustment with reason.
- `GET /api/admin/inventory/{id}/movements`: Complete ledger of inventory movements (`RESTOCK`, `SALE`, `RESERVE`, `RELEASE`, `ADJUST`, `RETURN`).

### 7.6 Orders Management
- `GET /api/admin/orders`: Filter by status, payment status, date range, search query.
- `GET /api/admin/orders/{orderNumber}`: Complete order detail with customer, timeline, items and `discountBreakdown` (same shape as the customer endpoint).
- `PATCH /api/admin/orders/{orderNumber}/status`: Update order status obeying state machine, recording reason `note` in timeline.
- `POST /api/admin/orders/{orderNumber}/ship`: Ship order with carrier and generate `KMTRK` tracking number.

### 7.7 Payments & Reconciliation
- `GET /api/admin/payments`: Filter payments by method, status, date.
- `GET /api/admin/payments/{id}`: Payment detail with gateway response and raw payload.
- `POST /api/admin/payments/{id}/refund`: Issue full or partial refund (sets order to `PARTIALLY_REFUNDED` or `REFUNDED`).
- `POST /api/admin/payments/{id}/recheck`: Run one Bakong check now (admin only). Returns `{paymentId, outcome, reason, bakongResponseCode, paymentStatus, checkCount}`; `outcome` is one of `PAID`, `LATE_PAID`, `ALREADY_PAID`, `NOT_FOUND`, `REJECTED`, `BAKONG_ERROR`, `SKIPPED`. A payment confirmed after expiry is recorded as PAID and its order gets `needsAttention=true`.
- `GET /api/admin/payments/reconciliation`: Reconcile internal payments against Bakong transaction logs.
- `GET /api/admin/payments/export.csv`: Export payment records to CSV.

### 7.8 Marketing: Coupons & Promotions
- `GET /api/admin/coupons`: List promotional coupons.
- `POST /api/admin/coupons`: Create coupon (`FIXED` or `PERCENTAGE`).
- `PUT /api/admin/coupons/{id}`: Update coupon.
- `DELETE /api/admin/coupons/{id}`: Delete coupon.
- `GET /api/admin/promotions`: List campaigns (`FLASH_SALE`, `SEASONAL`, `BOGO`).
- `POST /api/admin/promotions`: Create promotion campaign and evict home cache.
- `PUT /api/admin/promotions/{id}`: Update promotion campaign.
  - `AdminPromotionRequest.items`: `[{ productId, variantId?: Long, salePriceCents?: Long }]` (additive). `productIds: [Long]` still works but is deprecated; it maps to items without `variantId`. If both are sent, `items` wins. On PUT, omitting both leaves the promotion's products untouched; sending either replaces them.
  - Validation (`400 VALIDATION_ERROR`, `error.details` = `{ "items[i].<field>": message }`): `variantId` must belong to `productId`; a `FLASH_SALE` item for a product with variants must name a `variantId`; `salePriceCents` must be > 0 and < the variant's `priceCents` (the product's `priceCents` for a product-wide item); unknown products and duplicate product/variant pairs are rejected.
  - Multiple `FLASH_SALE` promotions may be active at once. The same variant (or product without variants) cannot be in two active `FLASH_SALE` promotions with overlapping time windows: `409 CONFLICT`, `error.details` = `{ "items[i]": "Variant V of product P is already in overlapping flash sale 'Name' (id N)" }`. When windows don't overlap the same variant may be reused. Each cart line is priced with the lowest sale price among all active flash sales that include it (still at most one automatic promotion per line).
  - `AdminPromotionDto` (GET list/detail, POST/PUT response) adds `items: [{ productId, productName, variantId?, sku?, attributes?, priceCents, salePriceCents? }]`; `productIds` is kept.
- `DELETE /api/admin/promotions/{id}`: Delete promotion campaign.

### 7.9 Reviews Moderation
- `GET /api/admin/reviews`: List reviews by moderation status (`PENDING`, `APPROVED`, `REJECTED`).
- `PATCH /api/admin/reviews/{id}`: Approve/Reject review with optional administrative reply.

### 7.10 Analytics & Reports
- `GET /api/admin/reports/sales`: (query `groupBy=day|month|category|product|paymentMethod`, `from`, `to`) Dynamic aggregation report.
- `GET /api/admin/reports/sales.csv`: Export sales report to CSV.
- `GET /api/admin/reports/sales.xlsx`: Export sales report to Microsoft Excel format.

### 7.11 System Settings & Audit Logs
- `GET /api/admin/settings`: Read global configurations (`khrRate`, `khqrMode`, merchant tags).
- `PUT /api/admin/settings`: Update settings (enforces `BAKONG_TOKEN` when switching to `LIVE`). `shippingMethods[]` entries are matched by `code` and update `feeCents` (must be >= 0, e.g. EXPRESS `5` = $0.05) and `freeOverCents` (optional/null = no free-shipping threshold, otherwise >= 0); unknown code -> 404, negative -> 422 `VALIDATION_ERROR`. New fees apply immediately to `GET /api/shipping/methods` and `POST /api/checkout/preview`.
- `GET /api/admin/audit-logs`: Audit trail listing backoffice writes with actor, action, entity, before/after JSON, and IP.
