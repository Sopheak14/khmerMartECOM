# 16-Component Checklist (C1–C16) → Frontend pages

Components are defined in `../back_end/README.md` §1. This table shows where each one is visible in the UI and which test proves it.

| # | Component | Frontend pages / components | Proof |
|:-:|---|---|---|
| C1 | Package-by-layer architecture | Mirrored in the frontend: `app/` (routes) · `components/` · `lib/` (API, money, stores) · `types/api.ts` (DTOs) | `npm run typecheck` |
| C2 | Authentication & dual JWT | `/login`, `/register`, `/forgot-password`, `/reset-password`, `/verify-email`, `/account/security`; `AuthProvider`, `apiClient` silent refresh | `auth.spec.ts`; session-restore test |
| C3 | Profile & addresses | `/account`, `/account/addresses` (25 provinces, one default), checkout `AddressStep` + `AddAddressDialog` | `auth.spec.ts` 1f, 1g |
| C4 | Catalog & disjunctive faceting | `/products` (`FilterSidebar`, chips, sort, pagination), `/category/[slug]`, header `SearchBar` suggestions | `catalog.spec.ts` 1–4 |
| C5 | Home page caching | `/` `HomeView`: flash-sale countdown, featured, bestsellers | `phase1.spec.ts`, `catalog.spec.ts` |
| C6 | Cart & pricing engine | `CartDrawer`, `/cart` (promotion lines, coupon SAVE5/WELCOME10/FREESHIP), product page per-variant sale price; admin `/admin/promotions` (per-variant flash sale) and `/admin/coupons` | `cart-checkout.spec.ts`, `admin-promotions.spec.ts` |
| C7 | Idempotent checkout & stock reservation | `/checkout` stepper → `ReviewStep` sends `Idempotency-Key`; `OUT_OF_STOCK` / `PRICE_CHANGED` redirect to cart | `cart-checkout.spec.ts` 5 & 6, 2b |
| C8 | Bakong KHQR payments & simulator | `/checkout/pay/[orderNumber]` (`KhqrCard`: QR, countdown, regenerate, expired overlay), `/orders/[n]/success`, admin `/admin/payments` | `khqr-orders.spec.ts` |
| C9 | PDF tax invoice | "Download invoice" on `/orders/[n]` and success page (`lib/downloadInvoice.ts`) | `khqr-orders.spec.ts` 4 |
| C10 | Order state machine | `/orders/[n]` status timeline, cancel / buy again; admin `/admin/orders/[n]` status transitions and invalid-transition error | `admin-core.spec.ts` 2 & 3 |
| C11 | Shipment & tracking | `/track`, `/track/[trackingNumber]` (EN/KM statuses), admin ship dialog + tracking events | `notifications-tracking-reviews.spec.ts` |
| C12 | Verified reviews & moderation | `ReviewDialog`, `ReviewsList` on the product page; admin `/admin/reviews` (approve / reject / reply) | teacher scenario; `admin-pages.spec.ts` 5 |
| C13 | Multi-channel notifications | `NotificationBell` dropdown + unread badge, `/account/notifications`, web-push toggle in `/account/security` | `notifications-tracking-reviews.spec.ts` |
| C14 | Backoffice administration & RBAC | `/admin/*` (products + variants + images + CSV, categories, inventory, orders, users); `AdminSidebar` hides links for STAFF; `AdminForbidden` 403 | `admin-core.spec.ts` 6 & 7, `admin-pages.spec.ts` |
| C15 | Dashboard & analytics | `/admin` KPIs and charts (`DashboardCharts`), `/admin/reports` chart + table + CSV/XLSX export | `admin-core.spec.ts` 1, `admin-pages.spec.ts` 7 |
| C16 | Security audit trail & AOP | `/admin/audit-logs` list with before/after JSON diff viewer | `admin-pages.spec.ts` 9 |
