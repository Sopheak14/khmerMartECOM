import { test, expect } from '@playwright/test';
import { createThrowawayUser, loginViaUI } from './helpers/auth';
import { selectHeroVariant } from './helpers/shop';
import { adminToken, createAddress, placeKhqrOrder } from './helpers/orders';
import { API, HERO } from './fixtures/catalog';

const PHNOM_PENH = { latitude: 11.5564, longitude: 104.9282 };

test.describe('Address map picker, no postal code, editable shipping fees', () => {
  test('add address with mocked geolocation and no postal code; coordinates are saved', async ({ page, context, request }) => {
    await context.grantPermissions(['geolocation']);
    await context.setGeolocation(PHNOM_PENH);

    const user = await createThrowawayUser(request);
    await loginViaUI(page, user.email, user.password, 'en');
    await page.goto('/en/account/addresses');
    await page.waitForLoadState('networkidle');

    // Account addresses form: no postal code field, map present
    await page.getByRole('button', { name: /Add New Address/i }).first().click();
    await expect(page.locator('#addr-postal')).toHaveCount(0);
    await expect(page.locator('[data-testid="location-map"]')).toBeVisible();
    await page.getByRole('button', { name: /Cancel/i }).first().click();

    // Checkout dialog
    await page.goto(`/en/products/${HERO.slug}`);
    await page.waitForLoadState('networkidle');
    await selectHeroVariant(page);
    await page.click('[data-testid="add-to-cart-button"]');
    await page.click('[data-testid="cart-drawer-close"]');
    await page.goto('/en/checkout');
    await page.waitForLoadState('networkidle');
    await page.click('[data-testid="add-new-address-btn"]');

    const dialog = page.locator('[data-testid="add-address-dialog"]');
    await expect(dialog).toBeVisible();
    await expect(dialog.locator('[data-testid="address-postal-input"]')).toHaveCount(0);
    // Previously-missing translation must not leak as a raw key
    await expect(dialog.locator('[data-testid="address-district-input"]')).toHaveAttribute('placeholder', /Daun Penh/);
    await expect(dialog.locator('[data-testid="location-map"]')).toBeVisible();

    // Stub the geocoder (no network dependence / respects 1 req per second policy)
    await page.route('https://nominatim.openstreetmap.org/**', (route) =>
      route.fulfill({
        json: { address: { road: 'Street 240', suburb: 'Chaktomuk', city_district: 'Daun Penh' } },
      })
    );
    await page.click('[data-testid="use-my-location-btn"]');
    await expect(page.locator('[data-testid="location-coords"]')).toHaveText('11.556400, 104.928200');
    await expect(page.locator('[data-testid="address-line1-input"]')).toHaveValue(/Street 240/);

    await page.fill('[data-testid="address-recipient-name-input"]', 'Map Tester');
    await page.fill('[data-testid="address-phone-input"]', '+85512345678');
    await page.fill('[data-testid="address-label-input"]', 'Pinned Home');

    const saved = page.waitForResponse((r) => r.url().includes('/users/me/addresses') && r.request().method() === 'POST');
    await page.click('[data-testid="save-address-btn"]');
    const res = await saved;
    expect(res.request().postDataJSON()).not.toHaveProperty('postalCode');
    expect(res.request().postDataJSON()).toMatchObject({ latitude: 11.5564, longitude: 104.9282 });
    expect(res.status()).toBeLessThan(300);
    const body = (await res.json()).data;
    expect(body.latitude).toBeCloseTo(11.5564, 4);
    expect(body.longitude).toBeCloseTo(104.9282, 4);
  });

  test('denied geolocation shows a clear error', async ({ page, context, request }) => {
    await context.clearPermissions();
    const user = await createThrowawayUser(request);
    await loginViaUI(page, user.email, user.password, 'en');
    await page.goto('/en/account/addresses');
    await page.getByRole('button', { name: /Add New Address/i }).first().click();
    await page.click('[data-testid="use-my-location-btn"]');
    await expect(page.locator('[data-testid="location-error"]')).toContainText(/permission|location/i);
  });

  test('admin edits one shipping fee row, checkout shows it, then the original fee is restored', async ({ page, request }) => {
    const token = await adminToken(request);
    const headers = { Authorization: `Bearer ${token}` };
    const before = (await (await request.get(`${API}/admin/settings`, { headers })).json()).data;
    const original = before.shippingMethods.find((m: any) => m.code === 'STANDARD');
    const originalText = (original.feeCents / 100).toFixed(2);

    try {
      await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
      await page.goto('/en/admin/settings');
      await page.waitForLoadState('networkidle');

      const row = page.locator('[data-testid="shipping-row-STANDARD"]');
      await expect(row.locator('[data-testid="shipping-fee-input-STANDARD"]')).toHaveCount(0);
      await row.locator('[data-testid="shipping-edit-STANDARD"]').click();

      // Validation: negative and 3 decimals are rejected without a request
      const feeInput = row.locator('[data-testid="shipping-fee-input-STANDARD"]');
      await feeInput.fill('-1');
      await row.locator('[data-testid="shipping-save-STANDARD"]').click();
      await expect(row).toContainText(/0 or more/i);
      await feeInput.fill('1.234');
      await row.locator('[data-testid="shipping-save-STANDARD"]').click();
      await expect(row).toContainText(/two decimal/i);

      // Cancel leaves the stored fee untouched
      await row.locator('[data-testid="shipping-cancel-STANDARD"]').click();
      await expect(row.locator('[data-testid="shipping-fee-text-STANDARD"]')).toHaveText(`$${originalText}`);

      // Valid edit
      await row.locator('[data-testid="shipping-edit-STANDARD"]').click();
      await feeInput.fill('7.77');
      // Hero cart is above the seeded free-shipping threshold; raise it so the fee is actually charged
      await row.locator('[data-testid="shipping-free-input-STANDARD"]').fill('9999.00');
      await row.locator('[data-testid="shipping-save-STANDARD"]').click();
      await expect(page.getByText('Shipping fee updated.')).toBeVisible();
      await expect(row.locator('[data-testid="shipping-fee-text-STANDARD"]')).toHaveText('$7.77');

      // Visible in checkout for a customer
      const user = await createThrowawayUser(request);
      await page.context().clearCookies();
      await loginViaUI(page, user.email, user.password, 'en');
      await page.goto(`/en/products/${HERO.slug}`);
      await page.waitForLoadState('networkidle');
      await selectHeroVariant(page);
      await page.click('[data-testid="add-to-cart-button"]');
      await page.click('[data-testid="cart-drawer-close"]');
      await page.goto('/en/checkout');
      await page.waitForLoadState('networkidle');
      await page.click('[data-testid="add-new-address-btn"]');
      await page.fill('[data-testid="address-recipient-name-input"]', 'Fee Tester');
      await page.fill('[data-testid="address-phone-input"]', '+85512345678');
      await page.fill('[data-testid="address-line1-input"]', 'Street 1');
      await page.fill('[data-testid="address-district-input"]', 'Sen Sok');
      await page.selectOption('[data-testid="address-province-select"]', 'Phnom Penh');
      await page.click('[data-testid="save-address-btn"]');
      await page.waitForSelector('[data-testid="add-address-dialog"]', { state: 'hidden' });
      await page.click('[data-testid="next-to-shipping-btn"]');
      await expect(page.locator('[data-testid="fee-STANDARD"]')).toContainText('7.77');
    } finally {
      // Always restore the original fee, even if an assertion failed
      const current = (await (await request.get(`${API}/admin/settings`, { headers })).json()).data;
      const restored = current.shippingMethods.map((m: any) =>
        m.code === 'STANDARD' ? { ...m, feeCents: original.feeCents, freeOverCents: original.freeOverCents } : m
      );
      await request.put(`${API}/admin/settings`, {
        headers,
        data: { storeName: current.storeName, khrRate: current.khrRate, khqrMode: current.khqrMode, shippingMethods: restored },
      });
      const after = (await (await request.get(`${API}/admin/settings`, { headers })).json()).data;
      expect(after.shippingMethods.find((m: any) => m.code === 'STANDARD').feeCents).toBe(original.feeCents);
    }
  });

  test('admin order detail shows "Open in map" only when the address has coordinates', async ({ page, request }) => {
    const withPin = await createThrowawayUser(request);
    const pinnedAddr = await createAddress(request, withPin, { latitude: 11.5564, longitude: 104.9282 });
    const pinned = await placeKhqrOrder(request, withPin, pinnedAddr);

    const noPin = await createThrowawayUser(request);
    const plainAddr = await createAddress(request, noPin);
    const plain = await placeKhqrOrder(request, noPin, plainAddr);

    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');

    await page.goto(`/en/admin/orders/${pinned.orderNumber}`);
    const link = page.locator('[data-testid="admin-order-open-map"]');
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute('href', /openstreetmap\.org\/\?mlat=11\.5564&mlon=104\.9282/);
    await expect(link).toHaveAttribute('target', '_blank');

    await page.goto(`/en/admin/orders/${plain.orderNumber}`);
    await expect(page.locator('[data-testid="admin-customer-name"]')).toBeVisible();
    await expect(page.locator('[data-testid="admin-order-open-map"]')).toHaveCount(0);
  });
});
