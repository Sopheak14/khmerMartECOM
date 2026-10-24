# KhmerMart E-Commerce Backend REST API

Production-ready Spring Boot backend for KhmerMart, a full-featured Cambodian e-commerce platform supporting the National Bank of Cambodia (NBC) Bakong KHQR payments, real-time tracking, multi-channel notifications, and complete administrative backoffice control.

---

## 1. 16-Component System Architecture Checklist

| # | System Component | Architectural Scope & Key Features | Primary Java Classes |
|:---:|:---|:---|:---|
| **C1** | **Package-by-Layer Architecture** | Standardized layer separation under `com.khmermart`: controllers, services, repositories, entities, models, DTOs, mappers, configs. | [`KhmerMartApplication.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/KhmerMartApplication.java)<br>[`WebConfig.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/config/WebConfig.java) |
| **C2** | **Authentication & Dual JWT** | Stateless 15-min access token + 7-day rotating `HttpOnly` refresh token. Instant revocation on deactivation or password change. | [`AuthServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/AuthServiceImpl.java)<br>[`JwtService.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/JwtService.java) |
| **C3** | **User Profile & Addresses** | Multi-address management, default delivery location flagging, Cambodian phone format validation. | [`AppUserServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/AppUserServiceImpl.java)<br>[`AddressServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/AddressServiceImpl.java) |
| **C4** | **Catalog & Disjunctive Faceting** | Multi-attribute filtering (RAM, color, storage), PostgreSQL trigram search (`pg_trgm`), disjunctive facet counts. | [`ProductServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/ProductServiceImpl.java)<br>[`CategoryServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CategoryServiceImpl.java) |
| **C5** | **Home Page Caching** | Aggregated home payload (featured categories, active flash sales, bestsellers) cached in Redis with zero N+1. | [`HomeServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/HomeServiceImpl.java)<br>[`CacheConfig.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/config/CacheConfig.java) |
| **C6** | **Cart & Pricing Engine** | Automatic promotions (flash sales, BOGO, seasonal percentage) + non-stacking coupon discount calculations. | [`CartServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CartServiceImpl.java)<br>[`PricingServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PricingServiceImpl.java) |
| **C7** | **Idempotent Checkout & Stock Reservation** | IETF `Idempotency-Key` header deduplication + atomic conditional SQL updates (`tryReserve`) preventing overselling under concurrency. | [`CheckoutServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/CheckoutServiceImpl.java)<br>[`IdempotencyFilter.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/IdempotencyFilter.java) |
| **C8** | **Bakong KHQR Payments & Simulator** | NBC EMVCo QR code string generation, CRC16 verification, 32-character MD5 hash tracking, and settlement simulation. | [`KhqrServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/KhqrServiceImpl.java)<br>[`BakongApiClient.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/BakongApiClient.java) |
| **C9** | **PDF Tax Invoice Engine** | OpenPDF automated generation of bilingual tax invoices with company seal, line item breakdowns, and QR payment proof. | [`InvoiceServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/InvoiceServiceImpl.java)<br>[`OrderController.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/controller/OrderController.java) |
| **C10** | **Order State Machine** | Strict transitions: `PENDING_PAYMENT` -> `CONFIRMED` -> `SHIPPED` -> `OUT_FOR_DELIVERY` -> `DELIVERED`, auto-settling COD. | [`OrderStateMachine.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/OrderStateMachine.java)<br>[`OrderServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/OrderServiceImpl.java) |
| **C11** | **Shipment & Tracking Lifecycle** | Generated `KMTRK` numbers, carrier event tracking (`PREPARING` -> `DISPATCHED` -> `IN_TRANSIT` -> `DELIVERED`). | [`TrackingServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/TrackingServiceImpl.java)<br>[`TrackingController.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/controller/TrackingController.java) |
| **C12** | **Verified Reviews & Moderation** | Review submission restricted to verified purchasers of delivered orders; backoffice review approval and reply. | [`ReviewServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/ReviewServiceImpl.java)<br>[`AdminReviewServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/AdminReviewServiceImpl.java) |
| **C13** | **Multi-Channel Notifications** | `@TransactionalEventListener(AFTER_COMMIT)` preventing ghost notifications: Bilingual Thymeleaf Emails, SMS, and Web Push. | [`NotificationEventListener.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/event/listener/NotificationEventListener.java)<br>[`PushServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PushServiceImpl.java) |
| **C14** | **Backoffice Administration & RBAC** | Fine-grained permissions (STAFF vs ADMIN), product variants/images CRUD, CSV import/export, and low-stock management. | [`AdminUserServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/AdminUserServiceImpl.java)<br>[`AdminProductServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/AdminProductServiceImpl.java) |
| **C15** | **Dashboard & Sales Analytics** | Invariant net revenue calculations, zero-filled daily chart series, CSV/XLSX export, and payment method breakdowns. | [`DashboardServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/DashboardServiceImpl.java)<br>[`ReportServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/ReportServiceImpl.java) |
| **C16** | **Security Audit Trail & AOP** | `@Audited` AOP aspect recording actor ID, action, entity, entityId, before/after JSON states, and client IP in `audit_logs`. | [`AuditAspect.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/aspect/AuditAspect.java)<br>[`AuditLogServiceImpl.java`](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/AuditLogServiceImpl.java) |

---

## 2. Quick Start

### Prerequisites
- Java 21 LTS (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`)
- Docker & Docker Compose
- Maven Wrapper (`./mvnw`)

### 1. Launch Supporting Containers
```bash
docker compose up -d
```
Starts:
- PostgreSQL 16 on port `5433` (container port 5432)
- Redis 7 on port `6380` (container port 6379)
- Mailpit on port `1025` (Web UI on `http://localhost:8025`)

### 2. Run Database Seeding & Launch Server
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw spring-boot:run
```
The server starts on port `4000`:
- Base API URL: `http://localhost:4000/api`
- Swagger Interactive UI: `http://localhost:4000/api/docs`
- Live OpenAPI Spec: `http://localhost:4000/api/v3/api-docs`

---

## 3. Seeded Demo Accounts

All demo passwords use BCrypt hashing:

| Email | Role | Password | Description / Permissions Scope |
|:---|:---:|:---:|:---|
| `admin@khmermart.local` | **ADMIN** | `Admin@12345` | Superuser: full access to catalog, users, settings, payments, audit logs. |
| `staff@khmermart.local` | **STAFF** | `Staff@12345` | Operations: catalog, orders, shipping, stock, reviews (403 on financial/audit). |
| `customer1@khmermart.local` | **CUSTOMER** | `Customer@123` | Verified shopper account with historical purchase records. |
| `customer2@khmermart.local` | **CUSTOMER** | `Customer@123` | Secondary customer account. |
| `customer3@khmermart.local` | **CUSTOMER** | `Customer@123` | Tertiary customer account. |

---

## 4. Switching to LIVE Bakong KHQR Mode

KhmerMart defaults to `MOCK` mode for deterministic local testing. To connect to the National Bank of Cambodia Live Gateway:
1. Obtain an official Production Access Token from NBC Bakong.
2. In [.env](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/.env.example) or via Backoffice API `PUT /api/admin/settings`:
   ```env
   KHQR_MODE=LIVE
   BAKONG_TOKEN=eyJh...<your-nbc-production-token>
   BAKONG_API_BASE=https://api-bakong.nbc.gov.kh
   ```
3. Switching to `LIVE` without a valid `BAKONG_TOKEN` automatically aborts with HTTP 422 `UNPROCESSABLE_ENTITY`.

---

## 5. Frontend & Mobile (Flutter) Connection

### Next.js / Web Frontend Connection
In your frontend `.env.local`:
```env
NEXT_PUBLIC_API_URL=http://localhost:4000/api
```
All responses follow the unified response envelope:
```json
{
  "success": true,
  "data": { ... },
  "meta": { "page": 1, "limit": 20, "total": 40, "totalPages": 2 }
}
```

### Flutter / Mobile Connection
For Android Emulator connecting to host machine:
```dart
const String apiBaseUrl = 'http://10.0.2.2:4000/api';
```
For iOS Simulator:
```dart
const String apiBaseUrl = 'http://localhost:4000/api';
```
Pass the Bearer JWT token in the `Authorization` header:
```dart
final headers = {
  'Content-Type': 'application/json',
  'Authorization': 'Bearer $accessToken',
  'Idempotency-Key': uuid.v4(),
};
```
