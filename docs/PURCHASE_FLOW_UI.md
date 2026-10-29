# Purchase flow — the 12 steps in the UI

Backend view of the same scenario: `../back_end/docs/PURCHASE_FLOW.md`. Each step names the screen, the React component that
owns it and the API call it makes (paths relative to `/api`). Screenshots are in `docs/screenshots/`.
Automated proof: `tests/e2e/notifications-tracking-reviews.spec.ts` ("Teacher 12-step scenario"), `khqr-orders.spec.ts`,
`cart-checkout.spec.ts`, `catalog.spec.ts`.

| # | Step | Screen (route) | Component | API call | Screenshot |
|:-:|---|---|---|---|---|
| 1 | Customer logs in | `/login` | `(auth)/login/page.tsx`, `AuthProvider` | `POST /auth/login` (access token in memory, `km_rt` cookie), `POST /cart/merge` | [login-en](screenshots/login-en.png) · [login-km](screenshots/login-km.png) |
| 2 | Search "kampot", then a $5–$10 price range | `/products?q=kampot&minPrice=5&maxPrice=10` | `SearchBar`, `ProductsCatalogView`, `FilterSidebar`, `ActiveFilterChips` | `GET /search/suggest`, `GET /products?q=&minPrice=&maxPrice=` | [search-autocomplete](screenshots/search-autocomplete.png) · [products-filters-en](screenshots/products-filters-en.png) |
| 3 | Open the hero product **1001 Takeo Hol Silk Sampot**: Premium $199.00 (no flash sale) vs Standard $129.00 with $149.00 struck through and a countdown | `/products/takeo-hol-silk-sampot` | `ProductDetailView`, `VariantSelector`, `ProductGallery`, `ReviewsList` | `GET /products/{slug}`, `GET /products/{id}/reviews` | [product-1001-en](screenshots/product-1001-en.png) · [product-1001-km](screenshots/product-1001-km.png) |
| 4 | Pick Ruby Red / Standard and add to cart | product page → cart drawer | `VariantSelector` (button stays disabled until the full combination is chosen), `CartDrawer` | `POST /cart/items` | [cart-en](screenshots/cart-en.png) |
| 5 | Apply coupon `SAVE5`: $149.00 − $20.00 flash − $5.00 coupon | `/cart` | `cart/page.tsx` (coupon input, promotion lines, net `-$5.00` line) | `POST /cart/coupon` | [cart-km-coupon](screenshots/cart-km-coupon.png) |
| 6 | Checkout with KHQR; the review total is **$124.00 ≈ 508,400 ៛** | `/checkout` (address → shipping → payment → review) | `CheckoutStepper`, `AddressStep`, `ShippingStep`, `PaymentStep`, `ReviewStep` | `GET /users/me/addresses`, `GET /shipping/methods`, `POST /checkout/preview`, `POST /checkout` with `Idempotency-Key` | [checkout-address](screenshots/checkout-address.png) · [checkout-review](screenshots/checkout-review.png) |
| 7 | Scan and pay | `/checkout/pay/{orderNumber}` | `KhqrCard`, `useKhqrPayment` (countdown, polling/SSE, regenerate, expired overlay) | `POST /payments/khqr/{n}`, `GET /payments/{id}/status`, `POST /payments/{id}/simulate-paid` (MOCK mode) | [khqr-pending-en](screenshots/khqr-pending-en.png) · [khqr-pending-km](screenshots/khqr-pending-km.png) · [khqr-paid](screenshots/khqr-paid.png) |
| 8 | Order confirmed | `/orders/{n}/success`, `/orders/{n}` | `success/page.tsx`, order detail timeline | `GET /orders/{n}` (status `CONFIRMED`) | [order-success-khqr](screenshots/order-success-khqr.png) · [order-detail](screenshots/order-detail.png) |
| 9 | Notifications arrive | header bell, `/account/notifications` | `NotificationBell`, `notifications/page.tsx` | `GET /notifications/unread-count`, `GET /notifications`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all` | [bell-dropdown-en](screenshots/bell-dropdown-en.png) · [notifications-km](screenshots/notifications-km.png) |
| 10 | Admin ships the order | `/admin/orders/{n}` | admin order detail, ship dialog | `POST /admin/orders/{n}/ship`, `POST /admin/orders/{n}/tracking-events`; customer follows `GET /track/{trackingNumber}` | [admin-ship-dialog](screenshots/admin-ship-dialog.png) · [track-en](screenshots/track-en.png) · [track-km](screenshots/track-km.png) |
| 11 | Delivered | `/admin/orders/{n}`, customer `/orders/{n}` | status transition control, order timeline | `PATCH /admin/orders/{n}/status` (`OUT_FOR_DELIVERY`, `DELIVERED`) | [admin-order-detail](screenshots/admin-order-detail.png) · [order-detail-delivered](screenshots/order-detail-delivered.png) |
| 12 | Verified review, then moderation | `/orders/{n}` → review dialog; `/admin/reviews`; product page | `ReviewDialog`, `StarRating`, `ReviewsList`, admin reviews page | `POST /products/{id}/reviews`, `PATCH /admin/reviews/{id}` (APPROVED), `GET /products/{id}/reviews` | [review-dialog](screenshots/review-dialog.png) · [admin-reviews](screenshots/admin-reviews.png) · [product-reviews](screenshots/product-reviews.png) |

## Rules the UI follows throughout

* Prices shown equal the backend DTO (`totalCents`, `discountCents`) — asserted in the E2E tests; KHR uses `khrRate`.
* A flash-sale price applies per variant: the product page shows the variant's `salePriceCents` with its regular price struck
  through, and nothing struck through for variants that are not on sale.
* Order statuses each have their own label (`orders.status.*`, shared by the customer, admin and tracking views):
  `OUT_FOR_DELIVERY` is "Out for delivery" / «កំពុងដឹកជញ្ជូន», never "Shipped".
* `OrderDTO` carries one `discountCents` (flash sale + coupon together), so the order summary shows a single "Discounts"
  line without the coupon code until the backend adds a breakdown (see `BACKEND_REQUESTS.md`).
* Checkout uses one `Idempotency-Key` per attempt, so a double click cannot create two orders.
* `OUT_OF_STOCK` shows "Only N left in stock" (EN/KM) on the cart; `PRICE_CHANGED` / `OUT_OF_STOCK` at checkout return the user to the cart.
* Every page above is covered by the zero-console-error and no-untranslated-key checks in EN and KM.
