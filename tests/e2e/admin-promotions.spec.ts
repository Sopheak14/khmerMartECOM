import { test, expect, Page } from '@playwright/test';
import { loginViaUI } from './helpers/auth';
import { selectHeroVariant } from './helpers/shop';
import { API, HERO, usd } from './fixtures/catalog';

// Royal Blue / Premium has no seeded flash sale, so the suite can sell it on promotion without overlapping the seed.
const VARIANT = HERO.variants.bluePremium;
const VARIANT_CENTS = HERO.premiumCents;
const SALE_CENTS = 17900;
const dollars = (cents: number) => (cents / 100).toFixed(2);

async function adminToken(request: import('@playwright/test').APIRequestContext) {
  const res = await request.post(`${API}/auth/login`, {
    data: { email: 'admin@khmermart.local', password: 'Admin@12345' },
  });
  expect(res.ok()).toBe(true);
  return (await res.json()).data.accessToken as string;
}

async function pickVariantInForm(page: Page, price: string, name: string) {
  await page.click('[data-testid="add-promotion-btn"]');
  await expect(page.locator('[data-testid="promotion-form-modal"]')).toBeVisible();
  await page.fill('[data-testid="promotion-name-input"]', name);
  await page.fill('[data-testid="promotion-form-modal"] input[placeholder="Filter products..."]', HERO.name);
  await page.click(`[data-testid="picker-toggle-${HERO.id}"]`);
  // Variants are listed with attributes, SKU and price
  const row = page.locator(`[data-testid="variant-row-${VARIANT}"]`);
  await expect(row).toContainText('Royal Blue');
  await expect(row).toContainText('Premium');
  await expect(row).toContainText('HOL-SAMPOT-BLU-PRM');
  await expect(row).toContainText(usd(VARIANT_CENTS));
  await page.check(`[data-testid="variant-checkbox-${VARIANT}"]`);
  await page.fill(`[data-testid="variant-sale-price-${VARIANT}"]`, price);
}

test.describe('Phase 8 — Admin per-variant flash sale', () => {
  test('flash sale on Royal Blue Premium at $179.00 -> storefront, cart (UI == API), conflict + validation messages, cleanup', async ({
    page,
    browser,
    request,
  }) => {
    test.setTimeout(90_000);
    await page.setViewportSize({ width: 1440, height: 900 });
    const token = await adminToken(request);
    const name = `E2E Variant Flash ${Date.now()}`;
    let promoId: number | undefined;

    try {
      await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
      await page.goto('/en/admin/promotions');
      await page.waitForLoadState('networkidle');

      // Client validation: sale price must be > 0 and < variant price
      await pickVariantInForm(page, dollars(VARIANT_CENTS), name);
      await page.click('[data-testid="promotion-submit-btn"]');
      await expect(page.locator(`[data-testid="variant-row-${VARIANT}"] [data-testid="picker-item-error"]`)).toContainText(
        `below ${usd(VARIANT_CENTS)}`
      );
      await page.fill(`[data-testid="variant-sale-price-${VARIANT}"]`, '0');
      await page.click('[data-testid="promotion-submit-btn"]');
      await expect(page.locator(`[data-testid="variant-row-${VARIANT}"] [data-testid="picker-item-error"]`)).toContainText(
        'greater than $0.00'
      );

      // Valid price -> created with items[]
      await page.fill(`[data-testid="variant-sale-price-${VARIANT}"]`, dollars(SALE_CENTS));
      const [createRes] = await Promise.all([
        page.waitForResponse((r) => r.url().endsWith('/api/admin/promotions') && r.request().method() === 'POST'),
        page.click('[data-testid="promotion-submit-btn"]'),
      ]);
      expect(createRes.status()).toBe(200);
      const sent = createRes.request().postDataJSON();
      expect(sent.items).toEqual([{ productId: HERO.id, variantId: VARIANT, salePriceCents: SALE_CENTS }]);
      promoId = (await createRes.json()).data.id;
      await expect(page.locator('[data-testid="promotion-form-modal"]')).toBeHidden();

      // Storefront (fresh guest): Royal Blue Premium shows the sale price with the regular price struck through
      const guestCtx = await browser.newContext();
      const shop = await guestCtx.newPage();
      await shop.setViewportSize({ width: 1280, height: 800 });
      await shop.goto(`/en/products/${HERO.slug}`);
      await shop.waitForLoadState('networkidle');
      await selectHeroVariant(shop, 'Premium', 'Royal Blue');
      await expect(shop.locator('[data-testid="product-price"]')).toHaveText(usd(SALE_CENTS));
      await expect(shop.locator('[data-testid="original-price"]')).toHaveText(usd(VARIANT_CENTS));

      const [cartRes] = await Promise.all([
        shop.waitForResponse((r) => r.url().includes('/api/cart/items') && r.request().method() === 'POST'),
        shop.click('[data-testid="add-to-cart-button"]'),
      ]);
      const cart = (await cartRes.json()).data;
      expect(cart.totalCents).toBe(SALE_CENTS);
      await expect(shop.locator('[data-testid="drawer-total-usd"]')).toHaveText(usd(SALE_CENTS));
      await guestCtx.close();

      // Overlapping flash sale on the same variant -> 409 message shown under that variant row
      await page.reload();
      await page.waitForLoadState('networkidle');
      await pickVariantInForm(page, dollars(SALE_CENTS - 1000), `${name} overlap`);
      await page.click('[data-testid="promotion-submit-btn"]');
      await expect(page.locator('[data-testid="promotion-form-error"]')).toBeVisible();
      await expect(page.locator(`[data-testid="variant-row-${VARIANT}"] [data-testid="picker-item-error"]`)).toBeVisible();
    } finally {
      if (promoId) {
        await request.delete(`${API}/admin/promotions/${promoId}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
      }
    }

    // After deleting the promotion the storefront is back to the regular Premium price
    const ctx = await browser.newContext();
    const shop2 = await ctx.newPage();
    await shop2.goto(`/en/products/${HERO.slug}`);
    await shop2.waitForLoadState('networkidle');
    await selectHeroVariant(shop2, 'Premium', 'Royal Blue');
    await expect(shop2.locator('[data-testid="product-price"]')).toHaveText(usd(VARIANT_CENTS));
    await expect(shop2.locator('[data-testid="original-price"]')).not.toBeVisible();
    await ctx.close();
  });
});
