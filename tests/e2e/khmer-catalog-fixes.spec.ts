import { test, expect, APIRequestContext } from '@playwright/test';
import { createThrowawayUser, loginViaUI } from './helpers/auth';
import { adminToken, cancelOrder, createAddress, placeKhqrOrder } from './helpers/orders';
import { API, CATEGORY, COUPON, HERO, SOAP, usd } from './fixtures/catalog';

type Locale = 'en' | 'km';

const OUT_FOR_DELIVERY_LABEL: Record<Locale, string> = { en: 'Out for delivery', km: 'កំពុងដឹកជញ្ជូន' };
const SHIPPED_LABEL: Record<Locale, string> = { en: 'Shipped', km: 'បានផ្ញើចេញ' };
const NOT_FOUND_TITLE: Record<Locale, RegExp> = { en: /Page Not Found/i, km: /រកមិនឃើញទំព័រ/ };
const DISCOUNTS_LABEL: Record<Locale, string> = { en: 'Discounts', km: 'ការបញ្ចុះតម្លៃ' };

/** A seeded customer1 order that is currently OUT_FOR_DELIVERY (the seed covers all 9 statuses). */
async function seededOutForDeliveryOrder(request: APIRequestContext): Promise<string> {
  const login = await request.post(`${API}/auth/login`, {
    data: { email: 'customer1@khmermart.local', password: 'Customer@123' },
  });
  const token = (await login.json()).data.accessToken;
  const res = await request.get(`${API}/orders`, {
    headers: { Authorization: `Bearer ${token}` },
    params: { status: 'OUT_FOR_DELIVERY', size: 20 },
  });
  const order = (await res.json()).data.find((o: { status: string }) => o.status === 'OUT_FOR_DELIVERY');
  expect(order, 'seed must contain an OUT_FOR_DELIVERY order for customer1').toBeTruthy();
  return order.orderNumber;
}

test.describe('Khmer catalog fixes', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
  });

  test('BOGO: 2 x 1033 Lemongrass Soap shows a -$3.50 promotion line in the cart', async ({ page, context }) => {
    await context.clearCookies();
    await page.goto(`/en/products/${SOAP.id}`);
    await page.waitForLoadState('networkidle');
    await page.click('[data-testid="qty-plus-btn"]');
    await expect(page.locator('[data-testid="qty-display"]')).toHaveText('2');
    await page.click('[data-testid="add-to-cart-button"]');
    await page.click('[data-testid="cart-drawer-close"]');

    await page.goto('/en/cart');
    await expect(page.locator('[data-testid="qty-value"]').first()).toHaveText('2');
    const promo = page.locator('[data-testid="promotion-discount-item"]');
    await expect(promo).toHaveCount(1);
    await expect(promo).toContainText(`-${usd(SOAP.bogoDiscountCents)}`);

    // UI == API
    const cart = await page.evaluate(async (api) => (await (await fetch(`${api}/cart`, { credentials: 'include' })).json()).data, API);
    expect(cart.promotionDiscounts).toHaveLength(1);
    expect(cart.promotionDiscounts[0].amountCents).toBe(SOAP.bogoDiscountCents);
  });

  for (const locale of ['en', 'km'] as const) {
    test(`5a: OUT_FOR_DELIVERY has its own label on the customer and admin timelines (${locale})`, async ({ page, request }) => {
      const orderNumber = await seededOutForDeliveryOrder(request);

      // Customer order detail: badge + timeline
      await loginViaUI(page, 'customer1@khmermart.local', 'Customer@123', locale);
      await page.goto(`/${locale}/orders/${orderNumber}`);
      await expect(page.locator('[data-testid="order-detail-status-badge"]')).toContainText(OUT_FOR_DELIVERY_LABEL[locale]);
      await expect(page.locator('[data-testid="order-detail-status-badge"]')).not.toContainText(SHIPPED_LABEL[locale]);
      const events = page.locator('[data-testid="timeline-event"]');
      await expect(events.last()).toContainText(OUT_FOR_DELIVERY_LABEL[locale]);
      await expect(events.last()).not.toContainText(SHIPPED_LABEL[locale]);

      // Customer order list badge
      await page.goto(`/${locale}/account/orders`);
      const card = page.locator('[data-testid="order-card"]', { hasText: orderNumber });
      await expect(card.locator('[data-testid="order-status-badge"]')).toContainText(OUT_FOR_DELIVERY_LABEL[locale]);
    });

    test(`5a: admin timeline uses the same labels (${locale})`, async ({ page, request }) => {
      const orderNumber = await seededOutForDeliveryOrder(request);
      await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', locale);
      await page.goto(`/${locale}/admin/orders/${orderNumber}`);
      const status = page.locator('[data-testid="admin-timeline-status"]').last();
      await expect(status).toHaveText(OUT_FOR_DELIVERY_LABEL[locale]);
    });

    test(`5a: tracking page shows OUT_FOR_DELIVERY with the same label (${locale})`, async ({ page, request }) => {
      const user = await createThrowawayUser(request);
      const addressId = await createAddress(request, user);
      const { orderNumber, trackingNumber } = await placeKhqrOrder(request, user, addressId, { ship: true });
      const token = await adminToken(request);
      for (const status of ['IN_TRANSIT', 'OUT_FOR_DELIVERY']) {
        const evt = await request.post(`${API}/admin/orders/${orderNumber}/tracking-events`, {
          headers: { Authorization: `Bearer ${token}` },
          data: { status, location: 'Phnom Penh', note: 'Courier update' },
        });
        expect(evt.ok(), `${status}: ${await evt.text()}`).toBe(true);
      }

      await page.goto(`/${locale}/track/${trackingNumber}`);
      await expect(page.locator('[data-testid="tracking-status-badge"]')).toContainText(OUT_FOR_DELIVERY_LABEL[locale]);
    });

    test(`5b: order summary shows a single "Discounts" line without the coupon code (${locale})`, async ({ page, request }) => {
      const user = await createThrowawayUser(request);
      const addressId = await createAddress(request, user);
      const { orderNumber } = await placeKhqrOrder(request, user, addressId, {
        productId: HERO.id,
        variantId: HERO.variants.redStandard,
        coupon: COUPON.save5,
      });
      try {
        // flash sale $20.00 + coupon $5.00 arrive as one discountCents; the label must not name only the coupon
        const dto = (await (await request.get(`${API}/orders/${orderNumber}`, { headers: { Authorization: `Bearer ${user.token}` } })).json()).data;
        expect(dto.discountCents).toBe(HERO.standardCents - HERO.flashCents + COUPON.save5Cents);

        await loginViaUI(page, user.email, user.password, locale);
        await page.goto(`/${locale}/orders/${orderNumber}`);
        const label = page.locator('[data-testid="order-discounts-label"]');
        await expect(label).toHaveText(DISCOUNTS_LABEL[locale]);
        await expect(label).not.toContainText(COUPON.save5);
        await expect(page.locator('[data-testid="order-discounts-amount"]')).toHaveText(`-${usd(dto.discountCents)}`);

        await page.context().clearCookies();
        await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', locale);
        await page.goto(`/${locale}/admin/orders/${orderNumber}`);
        const adminLabel = page.locator('[data-testid="admin-order-discounts-label"]');
        await expect(adminLabel).toContainText(DISCOUNTS_LABEL[locale]);
        await expect(adminLabel).not.toContainText(COUPON.save5);
        await expect(page.locator('[data-testid="admin-order-discounts-amount"]')).toHaveText(`-${usd(dto.discountCents)}`);
      } finally {
        await cancelOrder(request, user, orderNumber);
      }
    });

    test(`5c: unknown /admin route shows the 404 page (${locale})`, async ({ page }) => {
      await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', locale);
      await page.goto(`/${locale}/admin/does-not-exist`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(NOT_FOUND_TITLE[locale]);
      await expect(page.locator('body')).not.toBeEmpty();
    });

    test(`5d: STAFF has no Payments link and gets the 403 page on /admin/payments (${locale})`, async ({ page }) => {
      await loginViaUI(page, 'staff@khmermart.local', 'Staff@12345', locale);
      await page.goto(`/${locale}/admin`);
      await expect(page.locator('[data-testid="admin-nav-orders"]')).toBeVisible();
      await expect(page.locator('[data-testid="admin-nav-payments"]')).toHaveCount(0);

      await page.goto(`/${locale}/admin/payments`);
      await expect(page.locator('[data-testid="admin-forbidden"]')).toBeVisible();
      await expect(page.locator('[data-testid="admin-403-title"]')).toBeVisible();
    });

    test(`5d: ADMIN still sees the Payments link and page (${locale})`, async ({ page }) => {
      await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', locale);
      await page.goto(`/${locale}/admin`);
      await expect(page.locator('[data-testid="admin-nav-payments"]')).toBeVisible();
      await page.goto(`/${locale}/admin/payments`);
      await expect(page.locator('[data-testid="admin-forbidden"]')).toHaveCount(0);
    });

    test(`5e: an open admin dialog dims the whole viewport, including the top bar (${locale})`, async ({ page }) => {
      await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', locale);
      await page.goto(`/${locale}/admin/categories`);
      await page.click('[data-testid="add-category-btn"]');
      await expect(page.locator('[data-testid="category-form-modal"]')).toBeVisible();

      const overlayTop = await page.evaluate(() => {
        const dialog = document.querySelector('[data-testid="category-form-modal"]') as HTMLElement;
        return (dialog.parentElement as HTMLElement).getBoundingClientRect().top;
      });
      expect(overlayTop).toBe(0);

      // The element painted over the top bar is the overlay, not the header
      const topmostIsOverlay = await page.evaluate(() => {
        const dialog = document.querySelector('[data-testid="category-form-modal"]') as HTMLElement;
        const header = document.querySelector('header') as HTMLElement;
        const r = header.getBoundingClientRect();
        const el = document.elementFromPoint(r.left + r.width / 2, 8);
        return !!el && !header.contains(el) && dialog.parentElement!.contains(el);
      });
      expect(topmostIsOverlay).toBe(true);
    });
  }

  test('home hero and category cards come from the Khmer catalog, with no electronics copy', async ({ page, request }) => {
    for (const locale of ['en', 'km'] as const) {
      await page.goto(`/${locale}`);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await expect(page.getByText(locale === 'en' ? CATEGORY.silk.name : CATEGORY.silk.nameKm).first()).toBeVisible();
      const body = await page.innerText('body');
      expect(body).not.toMatch(/laptop|electronics|smartphone|fashion|កុំព្យូទ័រ/i);
    }
    const settings = await request.get(`${API}/settings/public`);
    expect(settings.ok()).toBe(true);
  });

  test('images that fail to load fall back to a placeholder: no broken image icon, no uncaught errors', async ({ page }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    // Simulate "no internet": every image request fails
    await page.route('**/_next/image**', (route) => route.abort());
    await page.goto('/en/products');
    await expect(page.locator('[data-testid="product-card"]').first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('[data-testid="product-card"] [data-testid="image-fallback"]').first()).toBeVisible();

    const brokenImages = await page.evaluate(() =>
      Array.from(document.querySelectorAll('img')).filter((img) => img.complete && img.naturalWidth === 0).length
    );
    expect(brokenImages).toBe(0);

    await page.goto('/en');
    await expect(page.locator('[data-testid="image-fallback"]').first()).toBeVisible({ timeout: 10000 });
    expect(pageErrors).toEqual([]);
  });
});
