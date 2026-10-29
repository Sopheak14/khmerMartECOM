# KhmerMart end-to-end smoke test / ការធ្វើតេស្ត Smoke ពីដើមដល់ចប់

`scripts/smoke_test.sh` drives the **running** backend through the teacher's 12-step purchase flow plus extra safety checks, so you can re-run it before the demo.
`scripts/smoke_test.sh` ដំណើរការលើ backend ដែល**កំពុងរត់** តាមដំណើរការទិញទំនិញ ១២ ជំហានរបស់លោកគ្រូ បូករួមទាំងការត្រួតពិនិត្យបន្ថែម ដើម្បីអាចដំណើរការឡើងវិញមុនពេលបង្ហាញ (demo)។

---

## English

### Prerequisites
- Backend running and reachable (default `http://localhost:4000`, override with `BASE=http://host:port`), **current build** (the script checks this in STEP 0 and stops if it is old), KHQR in **MOCK** mode.
- Seed data present: product 1001 (Takeo Hol Silk Sampot, variants 1–4 = Ruby Red / Royal Blue × Standard 14900 / Premium 19900), the active "Flash Sale 24H" (promotion 1: the two Standard variants 1 and 3 at 12900, plus Kampot Black Pepper 1006 at 790 and Dried Mango 1022 at 320), the "Pchum Ben Festival Sale" banner, coupon `SAVE5`, admin `admin@khmermart.local` (override with `ADMIN_EMAIL` / `ADMIN_PASSWORD`).
- Tools: `bash`, `curl`, `jq`, `pdftotext` (package `poppler-utils`), GNU `date`.

### How to run
```bash
scripts/smoke_test.sh                    # against http://localhost:4000
BASE=http://192.168.1.10:4000 scripts/smoke_test.sh
```
Each step prints `STEP n — name`, the HTTP status of every call, the key JSON fields, `✔` per assertion and `=> PASS`/`=> FAIL`; a summary table is printed at the end. The script stops at the **first** failure with exit code 1 (0 = all passed).

Safe to re-run: every run registers a new throwaway customer `smoke+<timestamp>@khmermart.local` (plus 10 more for the race test), never touches the seeded demo accounts, never starts/stops/restarts anything, and puts back any stock it changes (it tops up variants 1 and 4 by +20 through the admin API if fewer than 5 are available, so repeated runs do not run out of stock).

### What each step proves (teacher's 12 steps)

| Teacher's step | Script step | What it proves |
|---|---|---|
| (pre) | 0 | Backend reachable and is the current build (`GET /admin/promotions/1` returns per-variant `items`), flash sale still active. |
| 1 System health | 1 | `/api/health`: `db` ok, `redis` ok, `khqrMode` = MOCK. |
| 2 Register / login | 2 | New customer registers and logs in; `accessToken` is a JWT; `km_rt` refresh cookie is `HttpOnly`. |
| 3 Browse / search | 3 | 8 root categories (first `silk-textiles`); `products?q=silk` finds product 1001; product 1001: Standard variants 1 and 3 have `salePriceCents` 12900, Premium variants 2 and 4 have none (per-variant flash sale); products 1006 and 1022 carry their product-wide flash prices 790 and 320; the seasonal banner is "Pchum Ben Festival Sale". |
| 4 Add to cart | 4 | Variant 1 (Ruby Red Standard) costs 12900 after the 2000 flash discount; adding the Premium variant 2 gives a 19900 line with no extra flash discount; the Premium line is then removed so the cart holds the demo order (Standard ×1). |
| 5 Coupon | 5 | `SAVE5` gives the 500-cent coupon (total discount 2500 = 2000 flash + 500, cart total 12400); `INVALIDCODE` → 422 `COUPON_INVALID`. |
| 6 Address | 6 | Khmer recipient `សុភាព` and street `ផ្លូវ ២០០៤ ខណ្ឌពោធិ៍សែនជ័យ` are stored and returned unchanged (UTF-8). |
| 7 Shipping / preview | 7 | Shipping methods listed; `checkout/preview`: subtotal − discounts + shipping = total, and `totalKhr` = total/100 × `khrRate` (12400 → 508,400 ៛). |
| 8 Checkout | 8 | KHQR checkout with `Idempotency-Key` → `PENDING_PAYMENT`, stock reserved once; replay with the same key returns the **same** `orderNumber` and reserves nothing more. |
| 9 Pay with KHQR | 9 | QR string starts `000201`, md5 is 32 hex, `expiresAt` ≈ now + 10 min, status `PENDING`; `simulate-paid` → `PAID` / order `CONFIRMED`; a `SALE` inventory movement exists and available stock dropped by the quantity bought. |
| 10 Admin ships | 10 | Admin ships the order → `SHIPPED` + `KMTRK…` tracking number; tracking event added; public `GET /api/track/{number}` shows it. |
| 11 Notify / deliver / invoice | 11 | Unread notifications > 0; admin sets `DELIVERED`; `invoice.pdf` is `200 application/pdf` and its text contains `សុភាព` and `Coupon (SAVE5)`. |
| 12 Review | 12 | 5-star review accepted (201, verified purchase); a second review is rejected (409). |
| extra | X1 | COD order → customer cancel → stock released (an **unpaid** order writes a `RELEASE` movement; `RETURN` is only written for an order that was already paid). |
| extra | X2 | Paid KHQR order → cancel → payment `REFUNDED` and a `RETURN` movement, stock restored. |
| extra | X3 | No token → 401; customer on `/api/admin/dashboard` → 403. |
| extra | X4 | Quantity above stock → 409 `OUT_OF_STOCK`. |
| extra | X5 | Stock set to exactly 3, 10 customers check out at the same moment → exactly 3 succeed, 7 get 409 `OUT_OF_STOCK`, stock never negative; the 3 orders are cancelled and the stock restored. |

### Troubleshooting
- `STEP 0` fails with "promotion 1 returns per-variant items[]": the running backend is an old build — restart it from the latest commit, then re-run.
- `variant 1 on flash sale` fails: the seeded flash sale has ended (it lasts 7 days from seeding); run `scripts/reset_demo.sh` (see below) or create a new flash sale for the Standard variants 1 and 3 at 12900.
- A failed run can leave a few cancelled/pending orders for throwaway users; they are harmless.

---

## ខ្មែរ

### តម្រូវការជាមុន
- Backend កំពុងរត់ និងអាចចូលបាន (លំនាំដើម `http://localhost:4000` ប្តូរបានដោយ `BASE=http://host:port`) ជា **build ចុងក្រោយ** (ស្គ្រីបពិនិត្យនៅ STEP 0 ហើយឈប់ប្រសិនបើជា build ចាស់) និង KHQR ក្នុងរបៀប **MOCK**។
- មានទិន្នន័យ seed៖ ផលិតផល 1001 (សំពត់ហូលសូត្រ តាកែវ, variant 1–4 = ក្រហមរ៉ូប៊ី / ខៀវរាជា × Standard 14900 / Premium 19900), "Flash Sale 24H" ដែលកំពុងសកម្ម (promotion 1៖ variant Standard 1 និង 3 តម្លៃ 12900 ម្រេចខ្មៅកំពត 1006 តម្លៃ 790 និងស្វាយក្រៀម 1022 តម្លៃ 320), បដា "ពិធីបុណ្យភ្ជុំបិណ្ឌ", កូប៉ុង `SAVE5`, admin `admin@khmermart.local` (ប្តូរបានដោយ `ADMIN_EMAIL` / `ADMIN_PASSWORD`)។
- ឧបករណ៍៖ `bash`, `curl`, `jq`, `pdftotext` (កញ្ចប់ `poppler-utils`), `date` របស់ GNU។

### របៀបដំណើរការ
```bash
scripts/smoke_test.sh                    # ទៅកាន់ http://localhost:4000
BASE=http://192.168.1.10:4000 scripts/smoke_test.sh
```
ជំហាននីមួយៗបង្ហាញ `STEP n — ឈ្មោះ`, HTTP status នៃគ្រប់ការហៅ, ផ្នែក JSON សំខាន់ៗ, `✔` សម្រាប់ការត្រួតពិនិត្យនីមួយៗ និង `=> PASS`/`=> FAIL` ហើយមានតារាងសង្ខេបនៅចុងបញ្ចប់។ ស្គ្រីបឈប់នៅ **ការបរាជ័យដំបូង** ដោយ exit code 1 (0 = ជោគជ័យទាំងអស់)។

អាចដំណើរការឡើងវិញបានដោយសុវត្ថិភាព៖ រាល់ដងចុះឈ្មោះអតិថិជនបណ្តោះអាសន្នថ្មី `smoke+<timestamp>@khmermart.local` (និងបន្ថែម ១០ នាក់ទៀតសម្រាប់តេស្តប្រណាំងគ្នា) មិនប៉ះពាល់គណនី demo ដែលមានស្រាប់ មិនចាប់ផ្តើម/បិទ/ចាប់ផ្តើមឡើងវិញនូវអ្វីទាំងអស់ ហើយបូកស្តុកត្រឡប់មកវិញដែលវាបានប្តូរ (វាបន្ថែម +20 តាម admin API ប្រសិនបើ variant 1 និង 4 នៅសល់តិចជាង 5 ដើម្បីកុំឲ្យអស់ស្តុកពេលរត់ច្រើនដង)។

### អ្វីដែលជំហាននីមួយៗបញ្ជាក់ (១២ ជំហានរបស់លោកគ្រូ)

| ជំហានលោកគ្រូ | ជំហានស្គ្រីប | អ្វីដែលបញ្ជាក់ |
|---|---|---|
| (មុន) | 0 | Backend អាចចូលបាន និងជា build ចុងក្រោយ (`GET /admin/promotions/1` ត្រឡប់ `items` តាម variant) ហើយ flash sale នៅសកម្ម។ |
| 1 សុខភាពប្រព័ន្ធ | 1 | `/api/health`៖ `db` ok, `redis` ok, `khqrMode` = MOCK។ |
| 2 ចុះឈ្មោះ / ចូល | 2 | អតិថិជនថ្មីចុះឈ្មោះ និងចូលបាន; `accessToken` ជា JWT; cookie `km_rt` មាន `HttpOnly`។ |
| 3 រកមើល / ស្វែងរក | 3 | ប្រភេទឫសចំនួន ៨ (ទីមួយ `silk-textiles`); `products?q=silk` រកឃើញផលិតផល 1001; ផលិតផល 1001៖ variant Standard 1 និង 3 មាន `salePriceCents` 12900, variant Premium 2 និង 4 គ្មាន (flash sale តាម variant); ផលិតផល 1006 និង 1022 មានតម្លៃ flash ទាំងមូល 790 និង 320; បដារដូវកាលគឺ "ពិធីបុណ្យភ្ជុំបិណ្ឌ"។ |
| 4 ដាក់ក្នុងកន្ត្រក | 4 | Variant 1 (ក្រហមរ៉ូប៊ី Standard) តម្លៃ 12900 បន្ទាប់ពីបញ្ចុះ flash 2000; បន្ថែម variant Premium 2 ជាបន្ទាត់ 19900 គ្មានការបញ្ចុះតម្លៃ flash បន្ថែម; បន្ទាប់មកដកបន្ទាត់ Premium ចេញ ដើម្បីឲ្យកន្ត្រកនៅសល់ការបញ្ជាទិញ demo (Standard ×1)។ |
| 5 កូប៉ុង | 5 | `SAVE5` ឲ្យ ៥០០ សេន (បញ្ចុះសរុប 2500 = 2000 flash + 500 សរុបកន្ត្រក 12400); `INVALIDCODE` → 422 `COUPON_INVALID`។ |
| 6 អាសយដ្ឋាន | 6 | អ្នកទទួល `សុភាព` និងផ្លូវ `ផ្លូវ ២០០៤ ខណ្ឌពោធិ៍សែនជ័យ` ត្រូវបានរក្សាទុក និងត្រឡប់មកវិញដូចដើម (UTF-8)។ |
| 7 ដឹកជញ្ជូន / មើលជាមុន | 7 | បញ្ជីវិធីដឹកជញ្ជូន; `checkout/preview`៖ សរុបរង − បញ្ចុះតម្លៃ + ដឹកជញ្ជូន = សរុប ហើយ `totalKhr` = សរុប/100 × `khrRate` (12400 → 508,400 ៛)។ |
| 8 បង់ប្រាក់ (Checkout) | 8 | Checkout KHQR ជាមួយ `Idempotency-Key` → `PENDING_PAYMENT` រក្សាស្តុកម្តង; ហៅម្តងទៀតដោយ key ដដែលបាន `orderNumber` **ដដែល** ហើយមិនរក្សាស្តុកបន្ថែម។ |
| 9 បង់ដោយ KHQR | 9 | QR ចាប់ផ្តើមដោយ `000201`, md5 ៣២ តួ hex, `expiresAt` ≈ ឥឡូវ + ១០ នាទី, status `PENDING`; `simulate-paid` → `PAID` / ការបញ្ជាទិញ `CONFIRMED`; មាន movement `SALE` ហើយស្តុកដែលមានបានថយចុះតាមចំនួនទិញ។ |
| 10 Admin ដឹកជញ្ជូន | 10 | Admin ដឹកជញ្ជូន → `SHIPPED` + លេខតាមដាន `KMTRK…`; បន្ថែមព្រឹត្តិការណ៍តាមដាន; `GET /api/track/{លេខ}` សាធារណៈបង្ហាញវា។ |
| 11 ជូនដំណឹង / ប្រគល់ / វិក្កយបត្រ | 11 | ការជូនដំណឹងមិនទាន់អាន > 0; admin កំណត់ `DELIVERED`; `invoice.pdf` ជា `200 application/pdf` ហើយអត្ថបទមាន `សុភាព` និង `Coupon (SAVE5)`។ |
| 12 ការវាយតម្លៃ | 12 | ការវាយតម្លៃ ៥ ផ្កាយត្រូវបានទទួល (201, ទិញពិតប្រាកដ); ការវាយតម្លៃលើកទីពីរត្រូវបានបដិសេធ (409)។ |
| បន្ថែម | X1 | ការបញ្ជាទិញ COD → អតិថិជនលុបចោល → ស្តុកត្រឡប់មកវិញ (ការបញ្ជាទិញ **មិនទាន់បង់** កត់ត្រា movement `RELEASE`; `RETURN` កត់ត្រាតែពេលបានបង់រួចហើយ)។ |
| បន្ថែម | X2 | ការបញ្ជាទិញ KHQR ដែលបានបង់ → លុបចោល → ការបង់ប្រាក់ `REFUNDED` និង movement `RETURN` ស្តុកត្រឡប់មកវិញ។ |
| បន្ថែម | X3 | គ្មាន token → 401; អតិថិជនចូល `/api/admin/dashboard` → 403។ |
| បន្ថែម | X4 | ចំនួនលើសស្តុក → 409 `OUT_OF_STOCK`។ |
| បន្ថែម | X5 | កំណត់ស្តុកឲ្យនៅត្រឹម ៣ អតិថិជន ១០ នាក់ checkout ក្នុងពេលតែមួយ → ជោគជ័យ ៣ យ៉ាងពិតប្រាកដ, ៧ ទទួល 409 `OUT_OF_STOCK`, ស្តុកមិនអវិជ្ជមាន; ការបញ្ជាទិញទាំង ៣ ត្រូវលុបចោល ហើយស្តុកត្រឡប់មកដើមវិញ។ |

### ដោះស្រាយបញ្ហា
- `STEP 0` បរាជ័យ "promotion 1 returns per-variant items[]"៖ backend ដែលកំពុងរត់ជា build ចាស់ — សូមចាប់ផ្តើមឡើងវិញពី commit ចុងក្រោយ រួចដំណើរការម្តងទៀត។
- `variant 1 on flash sale` បរាជ័យ៖ flash sale ដែល seed ទុកបានផុតកំណត់ (មានរយៈពេល ៧ ថ្ងៃចាប់ពីពេល seed); សូមរត់ `scripts/reset_demo.sh` (មើលខាងក្រោម) ឬបង្កើត flash sale ថ្មីសម្រាប់ variant Standard 1 និង 3 តម្លៃ 12900។
- ការរត់ដែលបរាជ័យអាចទុកការបញ្ជាទិញដែលបានលុបចោល/កំពុងរង់ចាំខ្លះសម្រាប់អ្នកប្រើបណ្តោះអាសន្ន ហើយវាមិនមានគ្រោះថ្នាក់ទេ។

---

## Demo reset (`scripts/reset_demo.sh`) / កំណត់ការបង្ហាញឡើងវិញ

**English.** Puts the local dev database back to a fresh, seeded state so the demo always starts clean and the flash sale shows a live countdown (the seeder starts it at seed time and ends it 7 days later).

Exact order:
1. **Stop the backend** (Ctrl+C in its terminal). The script never stops or starts it, and it refuses to run while something answers on `BACKEND_URL` (default `http://localhost:4000`).
2. `scripts/reset_demo.sh` — type the database name to confirm (or pass `--yes`).
3. **Start the backend** again (`./mvnw spring-boot:run`). A backend that was running during the reset has stale connections and caches, so it must be restarted.
4. `scripts/smoke_test.sh` — must print `RESULT: ALL 18 STEPS PASSED`.

What it does: (a) refuses unless `SPRING_PROFILES_ACTIVE` is `dev` (default), the `DB_URL` host is `localhost`/`127.0.0.1`/`::1` and the database name does not look like production; (b) asks for confirmation; (c) `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`; (d) clears KhmerMart cache keys in Redis, then does a one-shot run of the application (`--app.seed.reset=true --app.seed.exit-after=true`, random port) that applies the Flyway migrations, runs the seeder and exits by itself; (e) prints the seeded flash sale window. Requires `psql`; `redis-cli` is optional. Config variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `BACKEND_URL` (defaults match `docker-compose.yml`).

**ខ្មែរ។** កំណត់មូលដ្ឋានទិន្នន័យ dev ក្នុងម៉ាស៊ីនឲ្យត្រឡប់ទៅទិន្នន័យ seed ថ្មីស្រស់ ដើម្បីឲ្យ demo ចាប់ផ្តើមស្អាត និង flash sale បង្ហាញម៉ោងរាប់ថយក្រោយពិតប្រាកដ (seeder ចាប់ផ្តើមវាពេល seed ហើយបញ្ចប់ក្រោយ ៧ ថ្ងៃ)។

លំដាប់ពិតប្រាកដ៖
1. **បិទ backend** (Ctrl+C ក្នុង terminal របស់វា)។ ស្គ្រីបមិនបិទ ឬបើកវាឡើយ ហើយបដិសេធមិនដំណើរការបើមានអ្វីឆ្លើយតបនៅ `BACKEND_URL` (លំនាំដើម `http://localhost:4000`)។
2. `scripts/reset_demo.sh` — វាយឈ្មោះមូលដ្ឋានទិន្នន័យដើម្បីបញ្ជាក់ (ឬប្រើ `--yes`)។
3. **ចាប់ផ្តើម backend** ឡើងវិញ (`./mvnw spring-boot:run`)។ backend ដែលកំពុងរត់ពេល reset មាន connection និង cache ចាស់ ដូច្នេះត្រូវចាប់ផ្តើមឡើងវិញ។
4. `scripts/smoke_test.sh` — ត្រូវបង្ហាញ `RESULT: ALL 18 STEPS PASSED`។

អ្វីដែលវាធ្វើ៖ (a) បដិសេធបើ `SPRING_PROFILES_ACTIVE` មិនមែន `dev` ម៉ាស៊ីនក្នុង `DB_URL` មិនមែន `localhost`/`127.0.0.1`/`::1` ឬឈ្មោះមូលដ្ឋានទិន្នន័យមើលទៅដូច production; (b) សួរការបញ្ជាក់; (c) `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`; (d) សម្អាត cache key របស់ KhmerMart ក្នុង Redis ហើយដំណើរការកម្មវិធីម្តងបណ្តោះអាសន្ន (`--app.seed.reset=true --app.seed.exit-after=true`, port ចៃដន្យ) ដែលអនុវត្ត Flyway migration, seed ទិន្នន័យ ហើយចេញដោយខ្លួនឯង; (e) បង្ហាញរយៈពេល flash sale ដែលបាន seed។ ត្រូវការ `psql`; `redis-cli` ជាជម្រើស។
