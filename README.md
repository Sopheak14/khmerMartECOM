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

### 3. Reset the demo to a fresh state (EN / ខ្មែរ)
Before a demo, put the dev database back to a clean seeded state (fresh data, flash sale live **from now for 7 days** so the countdown is running):
```bash
# 1. stop the backend yourself (Ctrl+C in its terminal)
scripts/reset_demo.sh            # asks you to type the database name; --yes skips the prompt
# 2. start the backend again   (the script never stops or starts it)
./mvnw spring-boot:run
# 3. check everything works
scripts/smoke_test.sh
```
Safety: the script refuses unless `SPRING_PROFILES_ACTIVE` is `dev`, the database host is `localhost`, and no backend answers on `BACKEND_URL` (default `http://localhost:4000`). It drops and re-creates the `public` schema, runs Flyway + the seeder, and clears KhmerMart cache keys in Redis. **The backend must be restarted after the reset.** Details: [`docs/SMOKE_TEST.md`](docs/SMOKE_TEST.md).

មុនពេល demo សូមកំណត់មូលដ្ឋានទិន្នន័យ dev ឲ្យត្រឡប់ទៅទិន្នន័យថ្មីស្រស់ (flash sale ដំណើរការ **ចាប់ពីពេលនេះរយៈពេល ៧ ថ្ងៃ** ដើម្បីឲ្យមានម៉ោងរាប់ថយក្រោយ)៖ ១) បិទ backend ដោយខ្លួនឯង ២) `scripts/reset_demo.sh` (វាសួរឲ្យវាយឈ្មោះមូលដ្ឋានទិន្នន័យ; `--yes` រំលងសំណួរ) ៣) ចាប់ផ្តើម backend ឡើងវិញ (ស្គ្រីបមិនបិទ/បើក backend ឡើយ) ៤) `scripts/smoke_test.sh`។ ស្គ្រីបបដិសេធបើមិនមែនប្រូហ្វាល `dev` មិនមែនម៉ាស៊ីន `localhost` ឬមាន backend កំពុងឆ្លើយតប។ **ត្រូវចាប់ផ្តើម backend ឡើងវិញបន្ទាប់ពី reset។**

### Seeded catalog (Khmer-only) / កាតាឡុកដែលបាន seed (ផលិតផលខ្មែរតែប៉ុណ្ណោះ)

KhmerMart sells **only Cambodian products**. The seed (`com.khmermart.seed.DataSeeder`, constants in `SeedCatalog.java`) creates **8 root categories** (Silk & Textiles, Spices & Sauces, Rice & Grains, Coffee & Tea, Snacks & Dried Fruit, Handicrafts, Beauty & Wellness, Books & Culture; each with a Khmer name) and **40 products** (ids 1001–1040, generic origin brands such as "Takeo Weavers" / "Kampot Farms"; the description holds an English and a Khmer paragraph).

| Item | Seed value |
|---|---|
| Hero **1001 Takeo Hol Silk Sampot** | 4 variants: Ruby Red / Royal Blue × Standard $149.00 / Premium $199.00, 50 in stock each |
| **Flash Sale 24H** (7 days from seed time) | 1001 Standard variants (1 and 3) at $129.00; 1006 Kampot Black Pepper at $7.90; 1022 Dried Mango at $3.20 |
| **Pchum Ben Festival Sale** / ពិធីបុណ្យភ្ជុំបិណ្ឌ (seasonal banner) | 10% off Spices & Sauces (1006–1012) |
| **Buy 1 Get 1 Lemongrass Soap** / ទិញ ១ ថែម ១ សាប៊ូស្លឹកគ្រៃ (BOGO, 7 days) | 1033 Lemongrass Soap, 2 units → 1 free (−$3.50) |
| Low stock | 1027 Silver Box (3 left) and 1004 Table Runner (4 left), threshold 5, so the dashboard "Low stock" table is populated |
| Coupons | `SAVE5` ($5 off, min $35), `WELCOME10`, `FREESHIP`, `EXPIRED` |
| Demo order | 1001 Standard → $149.00 − $20.00 flash − $5.00 `SAVE5` = **$124.00 = 508,400 ៛** (rate 4,100 ៛/$) |
| Orders / reviews | 32 orders covering all 9 statuses, 24 reviews, all built from the new catalog |

Product photos: only verified images are used (3 category photos: rice, spices, books); every other product and category shows a clean placeholder until real photos are uploaded in Admin → Products.

KhmerMart លក់តែ **ផលិតផលកម្ពុជា** ប៉ុណ្ណោះ។ ការ seed បង្កើតប្រភេទឫស ៨ (សូត្រ និងក្រណាត់, គ្រឿងទេស និងទឹកជ្រលក់, អង្ករ និងធញ្ញជាតិ, កាហ្វេ និងតែ, អាហារសម្រន់ និងផ្លែឈើស្ងួត, សិប្បកម្ម, សម្រស់ និងសុខភាព, សៀវភៅ និងវប្បធម៌) និងផលិតផល ៤០ (លេខ 1001–1040)។ ផលិតផលសំខាន់ **1001 សំពត់ហូលសូត្រ តាកែវ** មាន ៤ variant (ក្រហមរ៉ូប៊ី / ខៀវរាជា × Standard $149.00 / Premium $199.00); Flash Sale 24H បញ្ចុះតម្លៃ variant Standard ត្រឹម $129.00 បូកម្រេចខ្មៅកំពត $7.90 និងស្វាយក្រៀម $3.20; បដា "ពិធីបុណ្យភ្ជុំបិណ្ឌ" បញ្ចុះ ១០% លើគ្រឿងទេស និងទឹកជ្រលក់; ការបញ្ជាទិញ demo៖ $149.00 − $20.00 − $5.00 = **$124.00 = 508,400 ៛**។

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
