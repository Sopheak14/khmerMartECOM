# Test Execution & Coverage Report

## 1. Executive Summary

- **Total Test Classes**: 17
- **Total Tests Executed**: 88
- **Passed**: 88 (100%)
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Line Coverage (JaCoCo)**: **86.07%** (5,952 / 6,915 lines)
- **Instruction Coverage (JaCoCo)**: **85.14%** (27,713 / 32,551 instructions)
- **Build Status**: `BUILD SUCCESS` (Verified with OpenJDK 21 via `./mvnw clean verify`)

---

## 2. Test Execution per Class

| # | Test Class | Type | Tests | Failures | Errors | Skipped | Status |
|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | [`AddressIntegrationTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/AddressIntegrationTest.java) | Integration | 2 | 0 | 0 | 0 | ✅ PASSED |
| 2 | [`AppUserControllerTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/controller/AppUserControllerTest.java) | Controller | 4 | 0 | 0 | 0 | ✅ PASSED |
| 3 | [`AuthIntegrationTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/AuthIntegrationTest.java) | Integration | 10 | 0 | 0 | 0 | ✅ PASSED |
| 4 | [`CartIntegrationTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/CartIntegrationTest.java) | Integration | 4 | 0 | 0 | 0 | ✅ PASSED |
| 5 | [`CatalogIntegrationTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/CatalogIntegrationTest.java) | Integration | 8 | 0 | 0 | 0 | ✅ PASSED |
| 6 | [`CheckoutIntegrationTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/CheckoutIntegrationTest.java) | Integration | 6 | 0 | 0 | 0 | ✅ PASSED |
| 7 | [`ConcurrencyCheckoutIntegrationTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/ConcurrencyCheckoutIntegrationTest.java) | Concurrency | 1 | 0 | 0 | 0 | ✅ PASSED |
| 8 | [`HealthControllerIntegrationTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/HealthControllerIntegrationTest.java) | Integration | 1 | 0 | 0 | 0 | ✅ PASSED |
| 9 | [`OrderPaymentIntegrationTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/OrderPaymentIntegrationTest.java) | Integration | 8 | 0 | 0 | 0 | ✅ PASSED |
| 10 | [`Phase6NotificationTrackingReviewTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/Phase6NotificationTrackingReviewTest.java) | Integration | 7 | 0 | 0 | 0 | ✅ PASSED |
| 11 | [`Phase7AdminApiIntegrationTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/Phase7AdminApiIntegrationTest.java) | Integration | 12 | 0 | 0 | 0 | ✅ PASSED |
| 12 | [`PurchaseFlowE2ETest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/PurchaseFlowE2ETest.java) | E2E Scenario | 1 | 0 | 0 | 0 | ✅ PASSED |
| 13 | [`TransactionRollbackEventTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/integration/TransactionRollbackEventTest.java) | Transactional | 1 | 0 | 0 | 0 | ✅ PASSED |
| 14 | [`SecurityEndpointTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/security/SecurityEndpointTest.java) | Security | 4 | 0 | 0 | 0 | ✅ PASSED |
| 15 | [`BakongApiClientWireMockTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/service/BakongApiClientWireMockTest.java) | Mock Integration | 6 | 0 | 0 | 0 | ✅ PASSED |
| 16 | [`KhqrServiceTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/service/KhqrServiceTest.java) | Unit | 4 | 0 | 0 | 0 | ✅ PASSED |
| 17 | [`PricingServiceImplTest`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/test/java/com/khmermart/service/PricingServiceImplTest.java) | Unit | 9 | 0 | 0 | 0 | ✅ PASSED |
| | **TOTAL** | | **88** | **0** | **0** | **0** | **100% PASS** |

---

## 3. Key Integration & Scenario Tests

### 3.1. `PurchaseFlowE2ETest` (12-Step Full E2E Scenario)
Executes the full e-commerce lifecycle in a single end-to-end integration test:
1. **Login**: Authenticates Customer 1 (`sokha@example.com`) and extracts JWT bearer token.
2. **Search**: Searches catalog for `"Laptop"` with brand filter (`Dell`).
3. **Product Detail**: Retrieves product details and verifies variant options.
4. **Cart**: Adds variant to cart and checks session state.
5. **Apply Coupon**: Applies coupon `SAVE5` and validates subtotal, promotion, and coupon discount calculation.
6. **Checkout KHQR**: Submits checkout with address, triggers order creation, holds inventory, and generates dynamic KHQR string with MD5 hash.
7. **Simulate Payment**: Emits simulated payment webhook, verifying order state transitions from `PENDING_PAYMENT` to `CONFIRMED`.
8. **Inventory Movement**: Validates stock decrement with `SALE` movement type and stock balance preservation.
9. **Notification Delivery**: Checks dispatch of order confirmation email, SMS, and in-app notifications.
10. **Admin Shipping**: Staff/Admin marks order as `SHIPPED` with tracking number and carrier.
11. **Order Delivery**: Staff/Admin marks order as `DELIVERED`, triggering delivery confirmation notification with review CTA.
12. **Customer Review & Moderation**: Customer submits 5-star review, Admin approves review, and product rating recalculates.

### 3.2. `ConcurrencyCheckoutIntegrationTest`
- Simulates concurrent checkouts on the exact same product SKU with limited inventory.
- Utilizes `SELECT ... FOR UPDATE` pessimistic locking in [`InventoryRepository`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java).
- Verifies that zero overselling occurs and stock never drops below zero.

### 3.3. `BakongApiClientWireMockTest`
- Uses WireMock to stub external NBC Bakong payment gateway endpoints.
- Verifies MD5 transaction hash lookup, JWT Bearer token authentication against Bakong, and handling of network errors and non-200 responses.

---

## 4. JaCoCo Coverage Breakdown

```
========================================================================================
JACOCO COVERAGE SUMMARY
========================================================================================
Instructions Coverage: 85.14% (27,713 / 32,551 covered)
Line Coverage:         86.07% (5,952 / 6,915 covered)
Branch Coverage:       55.50% (1,252 / 2,256 covered)
========================================================================================
```

### Coverage by Package

| Package | Instruction Coverage | Line Coverage | Description |
|---|:---:|:---:|---|
| `com.khmermart.service.impl` | ~88% | ~89% | Core business logic, pricing, checkout, KHQR generation |
| `com.khmermart.controller.admin` | ~87% | ~88% | Admin REST APIs, export/import, audits, dashboard |
| `com.khmermart.controller` | ~90% | ~91% | Customer storefront, auth, cart, checkout, payments |
| `com.khmermart.security` | ~85% | ~86% | JWT filter, rate limiter, custom entrypoint & handlers |
| `com.khmermart.event.listener` | ~96% | ~97% | Async notifications, emails, SMS, low-stock alerts |
| `com.khmermart.aspect` | ~75% | ~70% | `@Audited` AOP aspect capturing before/after state |
| `com.khmermart.mapper` | ~72% | ~73% | MapStruct DTO and entity mappers |

---

## 5. How to Re-Run the Test Suite

```bash
# Ensure Java 21 is selected
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Run all tests and generate JaCoCo report
./mvnw clean verify

# View the generated HTML coverage report:
# target/site/jacoco/index.html
```
