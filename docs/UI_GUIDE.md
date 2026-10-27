# KhmerMart UI Guide

## Stack and layout

Next.js 14.2 App Router · React 18 · Tailwind 3 · TanStack Query 5 · Zustand 5 · next-intl 3 · Recharts 2 · Sonner toasts.

```
src/app/[locale]/(shop)/   storefront routes (home, catalog, product, cart, checkout, orders, track, account)
src/app/[locale]/(auth)/   login, register, forgot/reset password, verify email
src/app/[locale]/admin/    backoffice routes (dashboard, orders, products, inventory, ...)
src/components/            layout, home, catalog, product, cart, checkout, payment, admin, common, ui
src/lib/                   apiClient (fetch + refresh), money (USD/KHR), queryKeys, stores (zustand), helpers
src/types/api.ts           TypeScript mirror of the backend DTOs
messages/en.json, km.json  all UI strings (no hard-coded copy in components)
tests/unit, tests/e2e      Vitest and Playwright (real backend)
```

## Design tokens

Colors are HSL CSS variables in `src/app/globals.css`, exposed through `tailwind.config.ts`
(`bg-background`, `text-foreground`, `text-muted`, `bg-primary`, `text-success`, ...). A `.dark` set exists for every token.
Fonts: Inter (Latin) and Kantumruy Pro (Khmer), loaded with `next/font`.
Brand red `primary` = `hsl(350 85% 42%)`; Cambodian-flag blue is `secondary`.

Contrast rules (enforced by the axe E2E spec): body/muted text must reach 4.5:1 on its real background.
`--muted` and `--success` are deliberately dark in light mode; use `text-red-700`, `text-green-800`, `text-amber-800`
(not `-500/-600`) for text on tinted chips, and a lighter shade (`text-red-400`) on dark surfaces such as the admin sidebar.

## Internationalisation

Every route lives under `/[locale]` (`en`, `km`). Strings: `useTranslations('namespace')`; add keys to **both** JSON files.
API responses carry `name` and `nameKm`; components pick by `useLocale()`. An E2E test fails if a raw key
(e.g. `cart.title`) is visible on a page.

## Money

All amounts are integer cents. `formatUsd(129900)` → `$1,299.00`; `formatKhr(cents, rate)` → `≈ 5,325,900 ៛`
(`khrRate` comes from `GET /settings/public`, 4100 in MOCK). Dollar form inputs convert with `toCents()` in `src/lib/money.ts`.
Prices shown in the UI must equal the CartDTO/OrderDTO values — E2E tests assert `UI == API`.

## Data fetching and auth

* `apiClient()` unwraps the `{ success, data, error }` envelope, throws `ApiError(code, message, status, details)`,
  attaches the in-memory access token and retries once after a silent refresh on 401.
* The access token lives only in memory (Zustand); the 7-day rotating refresh token is an HttpOnly cookie.
* `AuthProvider` restores the session on load using the shared single-flight `refreshAccessToken()`.
  Authenticated requests wait until restore has finished, so a slow refresh can never make a signed-in user look like a guest
  (this was the cause of a flaky checkout redirect; regression test: "slow /auth/refresh").
* Server state = TanStack Query with keys from `src/lib/queryKeys.ts`; the guest cart is identified by the `km_guest` cookie
  and merged with `POST /cart/merge` at login.
* Backend validation errors: `ApiError.details` is `{ field: message }` and is shown under the matching input
  (admin promotion form maps `items[i].variantId` / `items[i].salePriceCents` / `items[i]` to the right variant row).

## Patterns

* Loading → skeletons or spinners; error → message + retry; empty → illustrated empty state with a call to action.
* Destructive actions use an in-page confirmation (remove cart item, delete promotion), never `window.confirm`.
* Every interactive element has a `data-testid` for Playwright; icons-only buttons have an `aria-label`.
* Responsive: mobile first; verified at 360px (no horizontal scroll) and desktop 1280px.

## Accessibility

`tests/e2e/a11y-responsive.spec.ts` runs axe-core (WCAG 2 A/AA) on Home, Product, Cart, Checkout, KHQR, Order detail, Track and
the Admin dashboard in EN and KM and requires **zero serious/critical violations**. Charts are `aria-hidden` and have an
equivalent `sr-only` list. Lighthouse (mobile, production build): Home 91 / 98, Product 94 / 96 (performance / accessibility).
