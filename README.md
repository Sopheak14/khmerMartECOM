# KhmerMart — Frontend

Next.js 14 (App Router) storefront and backoffice for the KhmerMart Spring Boot API.
Bilingual (English / ខ្មែរ) via `next-intl`, KHQR (Bakong) payments, Tailwind CSS, TanStack Query, Zustand.

## 1. Run the backend first

The frontend needs the API on `http://localhost:4000/api`. From `../back_end`:

```bash
docker compose up -d                                            # PostgreSQL :5433, Redis :6380, Mailpit :8025
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw spring-boot:run   # seeds demo data, listens on :4000
curl http://localhost:4000/api/health                           # {"success":true,"data":{"status":"ok",...,"khqrMode":"MOCK"}}
```

`GET /api/settings/public` must report `khqrMode: MOCK` and `khrRate: 4100` for the demo and the tests.
Backend details: `../back_end/README.md`. Reset the demo data with `../back_end/scripts/reset_demo.sh`.

## 2. Setup

```bash
npm install
cp .env.example .env.local   # or create it: values below are the defaults
```

| Variable | Default | Purpose |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:4000/api` | REST base URL |
| `NEXT_PUBLIC_UPLOADS_URL` | `http://localhost:4000` | product image uploads |
| `NEXT_PUBLIC_SITE_NAME` | `KhmerMart` | site name |

```bash
npm run dev        # http://localhost:3000 (redirects to /en)
npm run build && npm run start     # production build on :3000
```

Locales: `/en/...` and `/km/...`. Currency is USD with a live KHR equivalent (`≈ 508,400 ៛` for $124.00 at 4,100 ៛/$) from `khrRate`.

## 3. Demo accounts (seeded by the backend)

| Email | Password | Role |
|---|---|---|
| `admin@khmermart.local` | `Admin@12345` | ADMIN — all backoffice pages |
| `staff@khmermart.local` | `Staff@12345` | STAFF — no Users / Reports / Settings / Audit |
| `customer1@khmermart.local` | `Customer@123` | CUSTOMER with order history |
| `customer2@khmermart.local` | `Customer@123` | CUSTOMER |

Coupons: `SAVE5`, `WELCOME10`, `FREESHIP`. Mail (verify / reset links) is caught by Mailpit at `http://localhost:8025`.

### Demo catalog (Khmer-only, seeded by the backend)

40 Cambodian products in 8 categories (silk, spices, rice, coffee & tea, snacks, handicrafts, beauty, books). The UI shows
whatever the API returns; nothing about the catalog is hard-coded in `src/` (E2E tests read it from the API or from
`tests/e2e/fixtures/catalog.ts`, which mirrors the backend `SeedCatalog.java`).

| Demo | What to expect |
|---|---|
| Hero **1001 Takeo Hol Silk Sampot** | Premium **$199.00**, no flash sale. Standard **$129.00** with **$149.00** struck through and a countdown |
| Demo order | Standard → add to cart → `SAVE5` → review total **$124.00 ≈ 508,400 ៛** → KHQR → simulate paid |
| Filter demo | search `kampot`, then price $5–$10; plus the `/category/silk-textiles` page |
| BOGO | 2 × **1033 Lemongrass Soap** → promotion line **−$3.50** |
| Low stock (admin Dashboard) | **1027 Kampong Luong Silver Box** and **1004 Hol Silk Table Runner** |
| Seasonal banner | Pchum Ben Festival Sale / ពិធីបុណ្យភ្ជុំបិណ្ឌ (10% off Spices & Sauces) |

Images: product/category/banner photos are placeholders from `placehold.co`, three category photos from
`images.unsplash.com`, and admin uploads are served by the backend under `/uploads/...`. Exactly these hosts (plus
`localhost:4000`) are allowed in `next.config.mjs` → `images.remotePatterns`. Every image goes through
`components/common/SafeImage.tsx`, which swaps to a neutral placeholder if a URL fails (offline, 404), so there is never a
broken-image icon. Product descriptions are stored as "English paragraph + Khmer paragraph"; the product page shows only the
paragraph for the current locale (`lib/description.ts`).

## 4. Tests

| Command | What it runs |
|---|---|
| `npm run lint` | ESLint (`next lint`) |
| `npm run typecheck` | `tsc --noEmit` |
| `npm test` | Vitest unit tests (`tests/unit`) |
| `npm run test:e2e` | **`next build` then Playwright against `next start`** (production build) |

E2E tests hit the **real backend** (start it first — `tests/e2e/global-setup.ts` fails fast if `/api/health` is down) and use
throwaway users/data only. Playwright starts the production server itself (`webServer` in `playwright.config.ts`).
If a server is already on `:3000`, either stop it or run `PW_REUSE_SERVER=1 npx playwright test` (make sure it is a production build).

Suites in `tests/e2e`: auth, catalog, cart/checkout (+ session-restore race), KHQR & orders (the $124.00 demo), notifications/tracking/reviews,
admin core, admin pages, admin per-variant promotions, `khmer-catalog-fixes.spec.ts` (BOGO, order status labels, discounts line,
`/admin` 404, STAFF payments 403, dialog overlay, image fallback — EN and KM), `admin-backoffice-link.spec.ts`, and `a11y-responsive.spec.ts`
(axe-core: zero serious/critical violations on 8 pages in EN and KM, and no horizontal scroll at 360px).
The Playwright run refreshes the screenshots in `docs/screenshots/`. The E2E tests create real orders; run
`../back_end/scripts/reset_demo.sh` afterwards to get back to the clean demo data.

### Lighthouse (mobile, production build)

```bash
npm run build && npm run start &
CHROME_PATH=/usr/bin/google-chrome npx lighthouse http://localhost:3000/en --form-factor=mobile \
  --only-categories=performance,accessibility --chrome-flags="--headless=new --no-sandbox" \
  --output=html --output=json --output-path=docs/lighthouse/home
```

Saved reports: `docs/lighthouse/` (Home and Product).

## 5. Documentation

| Doc | Content |
|---|---|
| [`docs/UI_GUIDE.md`](docs/UI_GUIDE.md) | architecture, design tokens, i18n, data fetching, auth, accessibility rules |
| [`docs/SCREENS.md`](docs/SCREENS.md) | every route → API endpoints → screenshot |
| [`docs/PURCHASE_FLOW_UI.md`](docs/PURCHASE_FLOW_UI.md) | the 12-step purchase flow: screen → component → API call |
| [`docs/COMPONENT_CHECKLIST.md`](docs/COMPONENT_CHECKLIST.md) | the 16 lesson components C1–C16 mapped to pages |
| [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md) | copy of `../back_end/docs/API_CONTRACT.md` (includes `salePriceCents` and promotion `items`) |
| [`docs/BACKEND_REQUESTS.md`](docs/BACKEND_REQUESTS.md) | backend issues found while integrating |
