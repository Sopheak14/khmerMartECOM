# KhmerMart Security Permissions & Role Matrix

This document defines the Role-Based Access Control (RBAC) matrix for the KhmerMart REST API.

## Roles
- **🔓 Public (Anonymous)**: Anyone without authentication.
- **🔑 CUSTOMER**: Authenticated regular customer.
- **🛠 STAFF**: Authenticated staff member (order fulfillment, catalog, inventory).
- **👑 ADMIN**: Authenticated platform administrator (full access, reports, user control, payment management, settings).

---

## Role × Endpoint Access Matrix

| Method | Endpoint Path | Description | Anonymous | CUSTOMER | STAFF | ADMIN |
|---|---|---|:---:|:---:|:---:|:---:|
| **HEALTH** |
| `GET` | `/api/health` | System health check | ✅ | ✅ | ✅ | ✅ |
| **DOCS** |
| `GET` | `/api/docs`, `/api/v3/api-docs` | Swagger UI & OpenAPI Specification | ✅ | ✅ | ✅ | ✅ |
| **AUTH** |
| `POST` | `/api/auth/register` | Customer self-registration | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/auth/login` | Authentication (email/password) | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/auth/refresh` | Rotate refresh token | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/auth/logout` | Revoke session & clear cookie | ❌ | ✅ | ✅ | ✅ |
| `POST` | `/api/auth/forgot-password` | Request password reset email | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/auth/reset-password` | Reset password with token | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/auth/verify-email` | Verify email with 6-digit code (signs in) | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/auth/resend-verification` | Resend verification code | ✅ | ✅ | ✅ | ✅ |
| **USER PROFILE** |
| `GET` | `/api/users/me` | Fetch personal profile | ❌ | ✅ | ✅ | ✅ |
| `PATCH`| `/api/users/me` | Update name and phone | ❌ | ✅ | ✅ | ✅ |
| `PATCH`| `/api/users/me/password` | Change account password | ❌ | ✅ | ✅ | ✅ |
| **ADDRESS BOOK** |
| `GET` | `/api/users/me/addresses` | List addresses | ❌ | ✅ | ✅ | ✅ |
| `POST` | `/api/users/me/addresses` | Add new address (enforces 1 default) | ❌ | ✅ | ✅ | ✅ |
| `PATCH`| `/api/users/me/addresses/{id}` | Update address | ❌ | ✅ | ✅ | ✅ |
| `DELETE`| `/api/users/me/addresses/{id}` | Delete address | ❌ | ✅ | ✅ | ✅ |
| **CATALOG & SEARCH** |
| `GET` | `/api/categories/**` | Browse category hierarchy | ✅ | ✅ | ✅ | ✅ |
| `GET` | `/api/products/**` | Browse and view products | ✅ | ✅ | ✅ | ✅ |
| `GET` | `/api/search/**` | Full-text search and suggestions | ✅ | ✅ | ✅ | ✅ |
| `GET` | `/api/settings/public` | Public store configuration | ✅ | ✅ | ✅ | ✅ |
| `GET` | `/api/home` | Home page banners and widgets | ✅ | ✅ | ✅ | ✅ |
| **CART** |
| `GET` | `/api/cart` | Get cart (guest or user) | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/cart/items` | Add item to cart | ✅ | ✅ | ✅ | ✅ |
| `PATCH`| `/api/cart/items/{id}` | Update item quantity | ✅ | ✅ | ✅ | ✅ |
| `DELETE`| `/api/cart/items/{id}` | Remove item from cart | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/cart/coupon` | Apply coupon code | ✅ | ✅ | ✅ | ✅ |
| `DELETE`| `/api/cart/coupon` | Remove coupon | ✅ | ✅ | ✅ | ✅ |
| `POST` | `/api/cart/merge` | Merge guest cart after login | ❌ | ✅ | ✅ | ✅ |
| **SHIPPING & TRACKING** |
| `GET` | `/api/shipping/methods` | List shipping carriers | ✅ | ✅ | ✅ | ✅ |
| `GET` | `/api/shipping/provinces` | List Cambodian provinces | ✅ | ✅ | ✅ | ✅ |
| `GET` | `/api/track/{trackingNumber}` | Public tracking status | ✅ | ✅ | ✅ | ✅ |
| **CHECKOUT & ORDERS** |
| `POST` | `/api/checkout/preview` | Preview order totals | ❌ | ✅ | ✅ | ✅ |
| `POST` | `/api/checkout` | Submit order (idempotent) | ❌ | ✅ | ✅ | ✅ |
| `GET` | `/api/orders` | Customer order history | ❌ | ✅ | ✅ | ✅ |
| `GET` | `/api/orders/{orderNumber}` | View order details | ❌ | ✅ | ✅ | ✅ |
| `POST` | `/api/orders/{orderNumber}/cancel` | Cancel eligible order | ❌ | ✅ | ✅ | ✅ |
| `GET` | `/api/orders/{orderNumber}/invoice.pdf` | Download PDF invoice | ❌ | ✅ | ✅ | ✅ |
| **PAYMENTS (KHQR)** |
| `POST` | `/api/payments/khqr/{orderNumber}` | Generate KHQR QR | ❌ | ✅ | ✅ | ✅ |
| `POST` | `/api/payments/khqr/{orderNumber}/regenerate` | Regenerate expired KHQR | ❌ | ✅ | ✅ | ✅ |
| `GET` | `/api/payments/{paymentId}/status` | Check payment status | ❌ | ✅ | ✅ | ✅ |
| `GET` | `/api/payments/{paymentId}/stream` | SSE status stream | ✅ (with token) | ✅ | ✅ | ✅ |
| `POST` | `/api/payments/{paymentId}/simulate-paid`| Mock payment settlement | ❌ | ✅ | ✅ | ✅ |
| `POST` | `/api/payments/bank-transfer/{orderNumber}/slip` | Upload transfer slip | ❌ | ✅ | ✅ | ✅ |
| **ADMIN & STAFF OPERATIONS** |
| `GET` | `/api/admin/dashboard` | Executive KPI dashboard | ❌ | ❌ (403) | ✅ | ✅ |
| `GET` | `/api/admin/orders` | Manage customer orders & search | ❌ | ❌ (403) | ✅ | ✅ |
| `GET` | `/api/admin/orders/{orderNumber}` | View complete order detail | ❌ | ❌ (403) | ✅ | ✅ |
| `PATCH`| `/api/admin/orders/{orderNumber}/status` | Advance order state machine | ❌ | ❌ (403) | ✅ | ✅ |
| `POST` | `/api/admin/orders/{orderNumber}/ship` | Fulfill and generate tracking | ❌ | ❌ (403) | ✅ | ✅ |
| `GET` | `/api/admin/inventory` | Inventory list with lowStock filter | ❌ | ❌ (403) | ✅ | ✅ |
| `POST` | `/api/admin/inventory/{id}/restock` | Restock inventory (clears low-stock alert) | ❌ | ❌ (403) | ✅ | ✅ |
| `POST` | `/api/admin/inventory/{id}/adjust` | Stock adjustment with reason | ❌ | ❌ (403) | ✅ | ✅ |
| `GET` | `/api/admin/inventory/{id}/movements` | Inventory audit movements history | ❌ | ❌ (403) | ✅ | ✅ |
| `GET` | `/api/admin/products` | Manage product catalog & filters | ❌ | ❌ (403) | ✅ | ✅ |
| `POST` | `/api/admin/products` | Create product with variants & Khmer name | ❌ | ❌ (403) | ✅ | ✅ |
| `PUT` | `/api/admin/products/{id}` | Update product details | ❌ | ❌ (403) | ✅ | ✅ |
| `DELETE`| `/api/admin/products/{id}` | Delete product (soft delete if ordered) | ❌ | ❌ (403) | ✅ | ✅ |
| `POST` | `/api/admin/products/{id}/images` | Upload image (magic bytes verified) | ❌ | ❌ (403) | ✅ | ✅ |
| `PATCH`| `/api/admin/products/{id}/images/order` | Reorder product gallery images | ❌ | ❌ (403) | ✅ | ✅ |
| `GET` | `/api/admin/products/export.csv` | Export catalog to CSV | ❌ | ❌ (403) | ✅ | ✅ |
| `POST` | `/api/admin/products/import` | Import CSV with per-row validation | ❌ | ❌ (403) | ✅ | ✅ |
| `*` | `/api/admin/categories/**` | Category CRUD (blocks deletion with products) | ❌ | ❌ (403) | ✅ | ✅ |
| `*` | `/api/admin/coupons/**` | Coupon management & validation | ❌ | ❌ (403) | ✅ | ✅ |
| `*` | `/api/admin/promotions/**` | Promotion campaigns & home cache eviction | ❌ | ❌ (403) | ✅ | ✅ |
| `GET` | `/api/admin/reviews` | Moderate reviews | ❌ | ❌ (403) | ✅ | ✅ |
| `PATCH`| `/api/admin/reviews/{id}` | Approve, reject, or reply to review | ❌ | ❌ (403) | ✅ | ✅ |
| **ADMIN ONLY (👑)** |
| `GET` | `/api/admin/users` | List and search user directory | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `PATCH`| `/api/admin/users/{id}` | Update user role and active state | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `GET` | `/api/admin/payments` | Payment transaction registry | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `GET` | `/api/admin/payments/{id}` | Transaction detail with raw gateway payload | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `POST` | `/api/admin/payments/{id}/refund` | Process full / partial refund | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `GET` | `/api/admin/payments/reconciliation` | Payment reconciliation report | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `GET` | `/api/admin/payments/export.csv` | Export payments transaction CSV | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `GET` | `/api/admin/reports/sales` | Sales report (day, month, cat, prod, method) | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `GET` | `/api/admin/reports/sales.csv` | Export sales report as CSV | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `GET` | `/api/admin/reports/sales.xlsx` | Export sales report as Excel XLSX | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `GET` | `/api/admin/settings` | Retrieve complete store configuration | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `PUT` | `/api/admin/settings` | Update store settings (khrRate, khqrMode) | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `GET` | `/api/admin/audit-logs` | Comprehensive security & write audit trail | ❌ | ❌ (403) | ❌ (403) | ✅ |
