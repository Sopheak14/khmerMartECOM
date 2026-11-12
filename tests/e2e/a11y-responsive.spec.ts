import { test, expect, request as pwRequest, Browser } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { createThrowawayUser, loginViaUI, ThrowawayUser } from './helpers/auth';
import { HERO } from './fixtures/catalog';
import { cancelOrder, createAddress, findShippedTrackingNumber, placeKhqrOrder, placePaidLiveOrder, seedRefundPending } from './helpers/orders';

type Role = 'guest' | 'customer' | 'admin';
interface PageSpec {
  name: string;
  role: Role;
  path: (d: Data) => string;
}
interface Data {
  pending: string;
  tracking: string;
  refund: string;
  request: string;
}

const PAGES: PageSpec[] = [
  { name: 'Home', role: 'guest', path: () => '' },
  { name: 'Product', role: 'guest', path: () => `/products/${HERO.slug}` },
  { name: 'Cart', role: 'customer', path: () => '/cart' },
  { name: 'Checkout', role: 'customer', path: () => '/checkout' },
  { name: 'KHQR', role: 'customer', path: (d) => `/checkout/pay/${d.pending}` },
  { name: 'Order detail', role: 'customer', path: (d) => `/orders/${d.pending}` },
  { name: 'Track', role: 'guest', path: (d) => `/track/${d.tracking}` },
  { name: 'Admin dashboard', role: 'admin', path: () => '/admin' },
  { name: 'Order detail (refund pending)', role: 'customer', path: (d) => `/orders/${d.refund}` },
  { name: 'Admin order (refund card)', role: 'admin', path: (d) => `/admin/orders/${d.refund}` },
  { name: 'Admin order (cancel request banner)', role: 'admin', path: (d) => `/admin/orders/${d.request}` },
  { name: 'Admin payments (refund pending)', role: 'admin', path: () => '/admin/payments?status=REFUND_PENDING' },
];

let data: Data;
let customer: ThrowawayUser;
const summary: string[] = [];

// Refresh tokens rotate, so every context logs in on its own instead of sharing a saved session
async function openPage(browser: Browser, role: Role, locale: string, path: string, width: number, height: number) {
  const ctx = await browser.newContext({ viewport: { width, height } });
  const page = await ctx.newPage();
  if (role === 'customer') await loginViaUI(page, customer.email, customer.password, 'en');
  if (role === 'admin') await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
  await page.goto(`/${locale}${path}`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(500);
  return { ctx, page };
}

test.describe('Phase 8 — Accessibility (axe) & responsive (360px)', () => {
  test.describe.configure({ timeout: 90_000 });

  test.beforeAll(async () => {
    const api = await pwRequest.newContext();
    customer = await createThrowawayUser(api);
    const addressId = await createAddress(api, customer);
    // High-stock hero variant (Ruby Red / Standard); the order is cancelled in afterAll so no stock stays reserved
    const pending = await placeKhqrOrder(api, customer, addressId, { productId: HERO.id, variantId: HERO.variants.redStandard });
    const refund = await seedRefundPending(api, customer, addressId);
    const request = await placePaidLiveOrder(api, customer, addressId);
    await api.post('http://localhost:4000/api/orders/' + request.orderNumber + '/cancel-request', {
      headers: { Authorization: `Bearer ${customer.token}` },
      data: { reason: 'Ordered by mistake' },
    });
    data = { pending: pending.orderNumber, tracking: await findShippedTrackingNumber(api), refund: refund.orderNumber, request: request.orderNumber };
    // A cart line so /cart and /checkout render their populated states
    await api.post('http://localhost:4000/api/cart/items', {
      headers: { Authorization: `Bearer ${customer.token}` },
      data: { productId: HERO.id, variantId: HERO.variants.redStandard, quantity: 1 },
    });
    await api.dispose();
  });

  test.afterAll(async () => {
    const api = await pwRequest.newContext();
    await cancelOrder(api, customer, data.pending);
    await api.dispose();
    console.log('AXE SUMMARY (serious+critical violations per page):\n' + summary.join('\n'));
  });

  for (const locale of ['en', 'km'] as const) {
    for (const spec of PAGES) {
      test(`axe: ${spec.name} [${locale}] has zero serious/critical violations`, async ({ browser }) => {
        const { ctx, page } = await openPage(browser, spec.role, locale, spec.path(data), 1280, 900);

        const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
        const line = `${spec.name} [${locale}]: ${bad.length} serious/critical, ${results.violations.length} total`;
        summary.push(line);
        console.log(line);
        if (process.env.A11Y_REPORT) {
          for (const v of bad) {
            console.log(
              `  VIOLATION ${spec.name} [${locale}] ${v.id} (${v.impact}) x${v.nodes.length}: ` +
                v.nodes.slice(0, 4).map((n) => `${n.target.join(" ")} ${(n.html || "").slice(0, 160)} :: ${(n.failureSummary || '').split('\n')[1] || ''}`).join(' | ')
            );
          }
          await ctx.close();
          return;
        }
        expect(
          bad.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`),
          `axe violations on ${spec.name} ${locale}`
        ).toEqual([]);
        await ctx.close();
      });

      test(`360px: ${spec.name} [${locale}] has no horizontal scroll`, async ({ browser }) => {
        const { ctx, page } = await openPage(browser, spec.role, locale, spec.path(data), 360, 740);
        const width = await page.evaluate(() => document.documentElement.scrollWidth);
        console.log(`360px ${spec.name} [${locale}]: scrollWidth=${width}`);
        expect(width, `scrollWidth on ${spec.name} ${locale}`).toBeLessThanOrEqual(360);
        await ctx.close();
      });
    }
  }
});
