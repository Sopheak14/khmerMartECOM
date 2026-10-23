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
| `POST` | `/api/auth/verify-email` | Verify email address | ✅ | ✅ | ✅ | ✅ |
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
| `GET` | `/api/admin/orders/**` | Manage customer orders | ❌ | ❌ (403) | ✅ | ✅ |
| `PATCH`| `/api/admin/orders/{orderNumber}/status` | Advance shipment/delivery | ❌ | ❌ (403) | ✅ | ✅ |
| `POST` | `/api/admin/orders/{orderNumber}/ship` | Fulfill and generate tracking | ❌ | ❌ (403) | ✅ | ✅ |
| `GET` | `/api/admin/inventory/**` | Inventory overview & adjustments | ❌ | ❌ (403) | ✅ | ✅ |
| `POST` | `/api/admin/inventory/{id}/restock` | Restock inventory | ❌ | ❌ (403) | ✅ | ✅ |
| `*` | `/api/admin/products/**` | Manage product catalog & CSV | ❌ | ❌ (403) | ✅ | ✅ |
| `*` | `/api/admin/categories/**` | Manage categories | ❌ | ❌ (403) | ✅ | ✅ |
| `*` | `/api/admin/coupons/**` | Manage coupons | ❌ | ❌ (403) | ✅ | ✅ |
| `*` | `/api/admin/promotions/**` | Manage marketing campaigns | ❌ | ❌ (403) | ✅ | ✅ |
| `GET` | `/api/admin/reviews` | Moderate reviews | ❌ | ❌ (403) | ✅ | ✅ |
| **ADMIN ONLY (👑)** |
| `*` | `/api/admin/users/**` | User management & role assignment | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `*` | `/api/admin/payments/**` | Payment audit & refund control | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `*` | `/api/admin/reports/**` | Financial and sales reports | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `*` | `/api/admin/settings/**` | Global store settings configuration | ❌ | ❌ (403) | ❌ (403) | ✅ |
| `GET` | `/api/admin/audit-logs/**` | System security audit trail | ❌ | ❌ (403) | ❌ (403) | ✅ |
