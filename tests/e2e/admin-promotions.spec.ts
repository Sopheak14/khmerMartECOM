import { test, expect, Page } from '@playwright/test';
import { loginViaUI } from './helpers/auth';

const API = 'http://localhost:4000/api';

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
  await page.fill('[data-testid="promotion-form-modal"] input[placeholder="Filter products..."]', 'Laptop Pro 15');
  await page.click('[data-testid="picker-toggle-1001"]');
  // Variants are listed with attributes, SKU and price
  const row = page.locator('[data-testid="variant-row-3"]');
  await expect(row).toContainText('32GB');
  await expect(row).toContainText('LP15-GRY-32-1TB');
  await expect(row).toContainText('$1,299.00');
  await page.check('[data-testid="variant-checkbox-3"]');
  await page.fill('[data-testid="variant-sale-price-3"]', price);
}

test.describe('Phase 8 — Admin per-variant flash sale', () => {
  test('flash sale on 1001 variant 3 at $1,199.00 -> storefront, cart (UI == API), conflict + validation messages, cleanup', async ({
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
      await pickVariantInForm(page, '1299.00', name);
      await page.click('[data-testid="promotion-submit-btn"]');
      await expect(page.locator('[data-testid="variant-row-3"] [data-testid="picker-item-error"]')).toContainText(
        'below $1,299.00'
      );
      await page.fill('[data-testid="variant-sale-price-3"]', '0');
      await page.click('[data-testid="promotion-submit-btn"]');
      await expect(page.locator('[data-testid="variant-row-3"] [data-testid="picker-item-error"]')).toContainText(
        'greater than $0.00'
      );

      // Valid price -> created with items[]
      await page.fill('[data-testid="variant-sale-price-3"]', '1199.00');
      const [createRes] = await Promise.all([
        page.waitForResponse((r) => r.url().endsWith('/api/admin/promotions') && r.request().method() === 'POST'),
        page.click('[data-testid="promotion-submit-btn"]'),
      ]);
      expect(createRes.status()).toBe(200);
      const sent = createRes.request().postDataJSON();
      expect(sent.items).toEqual([{ productId: 1001, variantId: 3, salePriceCents: 119900 }]);
      promoId = (await createRes.json()).data.id;
      await expect(page.locator('[data-testid="promotion-form-modal"]')).toBeHidden();

      // Storefront (fresh guest): variant 32GB / 1TB shows $1,199.00 with $1,299.00 struck through
      const guestCtx = await browser.newContext();
      const shop = await guestCtx.newPage();
      await shop.setViewportSize({ width: 1280, height: 800 });
      await shop.goto('/en/products/laptop-pro-15');
      await shop.waitForLoadState('networkidle');
      await shop.click('[data-testid="variant-chip-ram-32GB"]');
      await shop.click('[data-testid="variant-chip-color-Space Gray"]');
      await shop.click('[data-testid="variant-chip-storage-1TB"]');
      await expect(shop.locator('[data-testid="product-price"]')).toHaveText('$1,199.00');
      await expect(shop.locator('[data-testid="original-price"]')).toHaveText('$1,299.00');

      const [cartRes] = await Promise.all([
        shop.waitForResponse((r) => r.url().includes('/api/cart/items') && r.request().method() === 'POST'),
        shop.click('[data-testid="add-to-cart-button"]'),
      ]);
      const cart = (await cartRes.json()).data;
      expect(cart.totalCents).toBe(119900);
      await expect(shop.locator('[data-testid="drawer-total-usd"]')).toHaveText('$1,199.00');
      await guestCtx.close();

      // Overlapping flash sale on the same variant -> 409 message shown under that variant row
      await page.reload();
      await page.waitForLoadState('networkidle');
      await pickVariantInForm(page, '1100.00', `${name} overlap`);
      await page.click('[data-testid="promotion-submit-btn"]');
      await expect(page.locator('[data-testid="promotion-form-error"]')).toBeVisible();
      await expect(page.locator('[data-testid="variant-row-3"] [data-testid="picker-item-error"]')).toBeVisible();
    } finally {
      if (promoId) {
        await request.delete(`${API}/admin/promotions/${promoId}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
      }
    }

    // After deleting the promotion the storefront is back to $1,299.00
    const ctx = await browser.newContext();
    const shop2 = await ctx.newPage();
    await shop2.goto('/en/products/laptop-pro-15');
    await shop2.waitForLoadState('networkidle');
    await shop2.click('[data-testid="variant-chip-ram-32GB"]');
    await shop2.click('[data-testid="variant-chip-color-Space Gray"]');
    await shop2.click('[data-testid="variant-chip-storage-1TB"]');
    await expect(shop2.locator('[data-testid="product-price"]')).toHaveText('$1,299.00');
    await expect(shop2.locator('[data-testid="original-price"]')).not.toBeVisible();
    await ctx.close();
  });
});
