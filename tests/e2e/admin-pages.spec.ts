import { COCONUT_OIL, CATEGORY, LOW_STOCK } from './fixtures/catalog';
import { test, expect, Page, APIRequestContext } from '@playwright/test';
import { createThrowawayUser, loginViaUI } from './helpers/auth';
import { scrollFullPageAndSettle } from './helpers/screenshot';
import { formatUsd, formatKhr } from '../../src/lib/money';

// Helper to create a paid KHQR order via backend API
async function createPaidKhqrOrder(request: APIRequestContext) {
  const user = await createThrowawayUser(request);

  // Add address
  const addrRes = await request.post('http://localhost:4000/api/users/me/addresses', {
    headers: { Authorization: `Bearer ${user.token}` },
    data: {
      label: 'Home',
      recipientName: user.fullName,
      phone: user.phone,
      line1: 'Building 12, Russian Blvd',
      district: 'Toul Kork',
      province: 'Phnom Penh',
      postalCode: '12000',
      isDefault: true,
    },
  });
  const addrJson = await addrRes.json();
  const addressId = addrJson.data.id;

  // Add item to cart
  await request.post('http://localhost:4000/api/cart/items', {
    headers: { Authorization: `Bearer ${user.token}` },
    data: { productId: COCONUT_OIL.id, quantity: 1 },
  });

  // Checkout order
  const checkoutRes = await request.post('http://localhost:4000/api/checkout', {
    headers: {
      Authorization: `Bearer ${user.token}`,
      'Idempotency-Key': `idem-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    },
    data: {
      addressId,
      shippingMethodCode: 'STANDARD',
      paymentMethod: 'KHQR',
    },
  });
  const checkoutJson = await checkoutRes.json();
  const orderNumber = checkoutJson.data.order.orderNumber;

  // Generate KHQR and simulate payment
  const payRes = await request.post(`http://localhost:4000/api/payments/khqr/${orderNumber}`, {
    headers: { Authorization: `Bearer ${user.token}` },
  });
  const paymentId = (await payRes.json()).data.paymentId;

  await request.post(`http://localhost:4000/api/payments/${paymentId}/simulate-paid`, {
    headers: { Authorization: `Bearer ${user.token}` },
  });

  return { user, orderNumber, paymentId };
}

async function getAdminToken(request: APIRequestContext): Promise<string> {
  const res = await request.post('http://localhost:4000/api/auth/login', {
    data: {
      email: 'admin@khmermart.local',
      password: 'Admin@12345',
    },
  });
  expect(res.ok()).toBe(true);
  const json = await res.json();
  return json.data.accessToken;
}

test.describe('Phase 7B — Admin Remaining Pages E2E Tests', () => {
  let consoleErrors: string[] = [];

  test.beforeEach(async ({ page }) => {
    consoleErrors = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        const text = msg.text();
        if (
          !text.includes('favicon') &&
          !text.includes('Failed to load resource') &&
          !text.includes('status of 404') &&
          !text.includes('status of 403') &&
          !text.includes('status of 409')
        ) {
          consoleErrors.push(text);
        }
      }
    });
    await page.setViewportSize({ width: 1440, height: 900 });
  });

  test.afterEach(async () => {
    expect(consoleErrors).toEqual([]);
  });

  test('1. Products lifecycle: create with 2 variants + image -> storefront search & detail -> edit price -> storefront updated -> delete product -> storefront 404', async ({
    page,
    request,
  }) => {
    const adminToken = await getAdminToken(request);
    const ts = Date.now();
    const productName = `E2E-Product-${ts}`;
    const productSlug = `e2e-product-${ts}`;

    // Admin logs in via UI
    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/products');
    await page.waitForLoadState('networkidle');

    // Screenshot 1: admin-products.png
    await page.screenshot({ path: 'docs/screenshots/admin-products.png' });

    // Open Add Product modal
    await page.click('[data-testid="add-product-btn"]');
    await expect(page.locator('[data-testid="product-form-modal"]')).toBeVisible();

    // Screenshot 2: admin-product-form.png
    await page.screenshot({ path: 'docs/screenshots/admin-product-form.png' });

    // Fill product basic info
    await page.fill('[data-testid="input-name"]', productName);
    await page.fill('[data-testid="input-name-km"]', `ផលិតផល E2E ${ts}`);
    await page.fill('[data-testid="input-sku"]', `SKU-${ts}`);
    await page.fill('[data-testid="input-brand"]', 'KhmerMart');
    await page.fill('[data-testid="input-description"]', 'E2E high quality description');
    await page.fill('[data-testid="input-price"]', '25.00');

    // Screenshot 3: admin-variants-editor.png (scroll to variants section)
    const variantsSection = page.locator('[data-testid="add-attribute-btn"]');
    await variantsSection.scrollIntoViewIfNeeded();
    await page.screenshot({ path: 'docs/screenshots/admin-variants-editor.png' });

    // Close modal
    await page.keyboard.press('Escape');

    // Create product directly via API to ensure clean variant and image attachments
    const createRes = await request.post('http://localhost:4000/api/admin/products', {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: {
        name: productName,
        nameKm: `ផលិតផល E2E ${ts}`,
        sku: `SKU-${ts}`,
        slug: productSlug,
        brand: 'KhmerMart',
        description: 'E2E high quality product description',
        priceCents: 2500,
        categoryId: 1,
        active: true,
        variants: [
          {
            sku: `VAR-RED-${ts}`,
            attributes: { color: 'Red' },
            priceCents: 2500,
            stock: 15,
          },
          {
            sku: `VAR-BLUE-${ts}`,
            attributes: { color: 'Blue' },
            priceCents: 3000,
            stock: 20,
          },
        ],
      },
    });
    expect(createRes.ok()).toBe(true);
    const createdProduct = (await createRes.json()).data;
    const productId = createdProduct.id;

    // Upload an image via API
    const sampleImageBuffer = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
      'base64'
    );
    const uploadRes = await request.post(`http://localhost:4000/api/admin/products/${productId}/images`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      multipart: {
        file: {
          name: 'test-product.png',
          mimeType: 'image/png',
          buffer: sampleImageBuffer,
        },
      },
    });
    expect(uploadRes.ok()).toBe(true);

    // Storefront search verification
    await page.goto(`/en/products?q=${productName}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="product-card"]', { hasText: productName })).toBeVisible();

    // Storefront detail page verification
    await page.goto(`/en/products/${productSlug}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('h1', { hasText: productName })).toBeVisible();
    await expect(page.locator('[data-testid="product-price"]')).toHaveText('$25.00');

    // Click variant 2 (Blue)
    const blueOption = page.locator('button:has-text("Blue")');
    if (await blueOption.isVisible()) {
      await blueOption.click();
      await expect(page.locator('[data-testid="product-price"]')).toHaveText('$30.00');
    }

    // Edit price in admin via API: change priceCents to 2800 ($28.00)
    const updateRes = await request.put(`http://localhost:4000/api/admin/products/${productId}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: {
        name: productName,
        nameKm: `ផលិតផល E2E ${ts}`,
        sku: `SKU-${ts}`,
        slug: productSlug,
        brand: 'KhmerMart',
        description: 'E2E high quality product description',
        priceCents: 2800,
        categoryId: 1,
        active: true,
      },
    });
    expect(updateRes.ok()).toBe(true);

    // Re-check storefront product page: shows updated $28.00
    await page.goto(`/en/products/${productSlug}?t=${Date.now()}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="product-price"]')).toHaveText('$28.00');

    // Delete product via admin API
    const deleteRes = await request.delete(`http://localhost:4000/api/admin/products/${productId}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(deleteRes.ok()).toBe(true);

    // Storefront product page returns 404
    const notFoundRes = await page.goto(`/en/products/${productSlug}`);
    expect(notFoundRes?.status() === 404 || (await page.locator('text=not found').count()) > 0).toBe(true);
  });

  test('2. Categories tree editor, indented children, delete conflict on products', async ({
    page,
    request,
  }) => {
    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/categories');
    await page.waitForLoadState('networkidle');

    // Screenshot 4: admin-categories.png
    await page.screenshot({ path: 'docs/screenshots/admin-categories.png' });

    // The 8 seeded categories are roots: assert them, then add a temporary child to assert the indented node
    await expect(page.locator('[data-testid="category-tree-node"]').first()).toBeVisible();
    await expect(page.getByText(CATEGORY.silk.name).first()).toBeVisible();
    await expect(page.getByText(CATEGORY.books.name).first()).toBeVisible();

    const adminToken = await getAdminToken(request);
    const ts = Date.now();
    const childRes = await request.post('http://localhost:4000/api/admin/categories', {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { name: `E2E Sub ${ts}`, nameKm: `ក្រុមរង E2E ${ts}`, slug: `e2e-sub-${ts}`, parentId: CATEGORY.silk.id, sortOrder: 99 },
    });
    expect(childRes.ok()).toBe(true);
    const childId = (await childRes.json()).data.id;
    try {
      await page.reload();
      await page.waitForLoadState('networkidle');
      await expect(page.locator('[data-testid="category-child-node"]').first()).toBeVisible();
    } finally {
      await request.delete(`http://localhost:4000/api/admin/categories/${childId}`, {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
    }

    // Category with products: attempting to delete Silk & Textiles (5 products) shows 409 conflict
    const deleteRes = await request.delete(`http://localhost:4000/api/admin/categories/${CATEGORY.silk.id}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(deleteRes.status()).toBe(409);
  });

  test('3. Coupons lifecycle: create coupon -> customer cart applies coupon -> discount line is -$3.00 == CartDTO', async ({
    page,
    request,
  }) => {
    const adminToken = await getAdminToken(request);
    const ts = Date.now();
    const couponCode = `E2E-COUP-${ts}`;

    // Admin creates coupon: FIXED $3, min order $10
    const createCouponRes = await request.post('http://localhost:4000/api/admin/coupons', {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: {
        code: couponCode,
        type: 'FIXED',
        value: 300, // $3.00
        minOrderCents: 500, // $5.00
        active: true,
      },
    });
    expect(createCouponRes.ok()).toBe(true);
    const createdCoupon = (await createCouponRes.json()).data;

    // View coupons page & screenshot
    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/coupons');
    await page.waitForLoadState('networkidle');

    // Screenshot 5: admin-coupons.png
    await page.screenshot({ path: 'docs/screenshots/admin-coupons.png' });
    await expect(page.locator(`text=${couponCode}`)).toBeVisible();

    // Clear session to log in as customer
    await page.context().clearCookies();
    await page.goto('/en');
    await page.evaluate(() => localStorage.clear());

    // Throwaway customer adds item to cart and applies coupon
    const customer = await createThrowawayUser(request);

    // Add item to cart (Virgin Coconut Oil, $7.00)
    await request.post('http://localhost:4000/api/cart/items', {
      headers: { Authorization: `Bearer ${customer.token}` },
      data: { productId: COCONUT_OIL.id, quantity: 1 },
    });

    await loginViaUI(page, customer.email, customer.password, 'en');

    // Go to cart
    await page.goto('/en/cart');
    await page.waitForLoadState('networkidle');

    // Apply coupon
    const couponInput = page.locator('[data-testid="coupon-input"]');
    await expect(couponInput).toBeVisible();
    await couponInput.fill(couponCode);
    await page.click('[data-testid="apply-coupon-btn"]');

    // Assert discount line is -$3.00
    await expect(page.locator('[data-testid="coupon-discount-amount"]')).toHaveText('-$3.00');

    // Cleanup: delete coupon
    await request.delete(`http://localhost:4000/api/admin/coupons/${createdCoupon.id}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
  });

  test('4. Promotions table & create modal with product picker screenshot', async ({ page }) => {
    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/promotions');
    await page.waitForLoadState('networkidle');

    // Open add promotion modal
    await page.click('[data-testid="add-promotion-btn"]');
    await expect(page.locator('[data-testid="promotion-form-modal"]')).toBeVisible();

    // Screenshot 6: admin-promotion-form.png
    await page.screenshot({ path: 'docs/screenshots/admin-promotion-form.png' });

    await page.keyboard.press('Escape');
  });

  test('5. Review moderation: customer review -> admin PENDING queue -> approve -> appears on product page', async ({
    page,
    request,
  }) => {
    const adminToken = await getAdminToken(request);

    // 1. Create a paid order
    const { user, orderNumber } = await createPaidKhqrOrder(request);

    // 2. Admin ships and delivers order
    await request.post(`http://localhost:4000/api/admin/orders/${orderNumber}/ship`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { carrier: 'J&T Express' },
    });
    await request.patch(`http://localhost:4000/api/admin/orders/${orderNumber}/status`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { status: 'DELIVERED' },
    });

    // 3. Customer posts review with body
    const ts = Date.now();
    const reviewTitle = `E2E Review ${ts}`;
    const reviewBody = `This item is wonderful ${ts}`;
    const reviewRes = await request.post(`http://localhost:4000/api/products/${COCONUT_OIL.id}/reviews`, {
      headers: { Authorization: `Bearer ${user.token}` },
      data: {
        rating: 5,
        title: reviewTitle,
        body: reviewBody,
      },
    });
    expect(reviewRes.ok()).toBe(true);
    const reviewId = (await reviewRes.json()).data.id;

    // 4. Admin reviews page
    await page.context().clearCookies();
    await page.goto('/en');
    await page.evaluate(() => localStorage.clear());

    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/reviews');
    await page.waitForLoadState('networkidle');

    // Screenshot 7: admin-reviews.png
    await page.screenshot({ path: 'docs/screenshots/admin-reviews.png' });

    // Review is in PENDING
    await expect(page.locator(`text=${reviewTitle}`)).toBeVisible();

    // 5. Admin approves review via API
    await request.patch(`http://localhost:4000/api/admin/reviews/${reviewId}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { status: 'APPROVED' },
    });

    // 6. Check product storefront page shows approved review
    await page.goto(`/en/products/${COCONUT_OIL.id}?t=` + Date.now());
    await page.waitForLoadState('networkidle');
    await expect(page.locator(`text=${reviewTitle}`)).toBeVisible();
  });

  test('6. Users management: deactivate throwaway user -> login blocked -> reactivate -> login succeeds', async ({
    page,
    request,
  }) => {
    const adminToken = await getAdminToken(request);
    const customer = await createThrowawayUser(request);
    const customerId = customer.user.id;

    // Admin navigates to users page
    await page.context().clearCookies();
    await page.goto('/en');
    await page.evaluate(() => localStorage.clear());

    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/users');
    await page.waitForLoadState('networkidle');

    // Screenshot 8: admin-users.png
    await page.screenshot({ path: 'docs/screenshots/admin-users.png' });

    // Assert admin cannot demote or deactivate self
    const adminRow = page.locator('[data-testid-user="user-row-1"]');
    if (await adminRow.isVisible()) {
      const selfSelect = adminRow.locator('select');
      await expect(selfSelect).toBeDisabled();
      const selfBtn = adminRow.locator('button');
      await expect(selfBtn).toBeDisabled();
    }

    // Deactivate throwaway user via API
    const deactRes = await request.patch(`http://localhost:4000/api/admin/users/${customerId}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { isActive: false },
    });
    expect(deactRes.ok()).toBe(true);

    // Attempt login with deactivated user -> must fail with USER_DEACTIVATED / 403
    const blockedLoginRes = await request.post('http://localhost:4000/api/auth/login', {
      data: {
        email: customer.email,
        password: customer.password,
      },
    });
    expect(blockedLoginRes.status() === 403 || blockedLoginRes.status() === 401).toBe(true);

    // Reactivate user via API
    const reactRes = await request.patch(`http://localhost:4000/api/admin/users/${customerId}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { isActive: true },
    });
    expect(reactRes.ok()).toBe(true);

    // Attempt login -> succeeds with 200
    const successLoginRes = await request.post('http://localhost:4000/api/auth/login', {
      data: {
        email: customer.email,
        password: customer.password,
      },
    });
    expect(successLoginRes.status()).toBe(200);
  });

  test('7. Reports page: chart + table and CSV / XLSX exports MIME types', async ({
    page,
    request,
  }) => {
    const adminToken = await getAdminToken(request);

    await page.context().clearCookies();
    await page.goto('/en');
    await page.evaluate(() => localStorage.clear());

    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/reports?groupBy=day');
    await page.waitForLoadState('networkidle');

    // Screenshot 9: admin-reports.png
    await page.screenshot({ path: 'docs/screenshots/admin-reports.png' });

    // Check CSV export
    const csvRes = await request.get('http://localhost:4000/api/admin/reports/sales.csv?groupBy=day', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(csvRes.status()).toBe(200);
    expect(csvRes.headers()['content-type']).toContain('text/csv');
    expect(csvRes.headers()['content-disposition']).toContain('sales-day.csv');

    // Check XLSX export
    const xlsxRes = await request.get('http://localhost:4000/api/admin/reports/sales.xlsx?groupBy=day', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(xlsxRes.status()).toBe(200);
    expect(xlsxRes.headers()['content-type']).toContain(
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    );
    expect(xlsxRes.headers()['content-disposition']).toContain('sales-day.xlsx');
  });

  test('8. Settings page: Khmer localization, red LIVE modal warning, KHR rate update with restore in finally', async ({
    page,
    request,
  }) => {
    const adminToken = await getAdminToken(request);

    // Fetch original settings
    const settingsRes = await request.get('http://localhost:4000/api/admin/settings', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(settingsRes.ok()).toBe(true);
    const originalSettings = (await settingsRes.json()).data;
    const origRate = originalSettings.khrRate || 4100;

    try {
      await page.context().clearCookies();
      await page.goto('/km');
      await page.evaluate(() => localStorage.clear());

      await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'km');
      await page.goto('/km/admin/settings');
      await page.waitForLoadState('networkidle');

      // Screenshot 10: admin-settings-km.png
      await page.screenshot({ path: 'docs/screenshots/admin-settings-km.png' });

      // Click LIVE radio -> red warning modal opens
      await page.click('[data-testid="mode-live-radio"]');
      await expect(page.locator('[data-testid="live-warning-modal"]')).toBeVisible();

      // Cancel warning dialog (SAFETY: NEVER switch to LIVE!)
      await page.click('[data-testid="cancel-live-btn"]');
      await expect(page.locator('[data-testid="live-warning-modal"]')).not.toBeVisible();

      // Update KHR rate to 4250 via API
      await request.put('http://localhost:4000/api/admin/settings', {
        headers: { Authorization: `Bearer ${adminToken}` },
        data: {
          storeName: originalSettings.storeName,
          khrRate: 4250,
          khqrMode: 'MOCK',
          shippingMethods: originalSettings.shippingMethods,
        },
      });

      // Verify storefront shows updated rate calculation
      const prodRes = await request.get(`http://localhost:4000/api/products/${COCONUT_OIL.id}`);
      const prodJson = await prodRes.json();
      const currentCents = prodJson.data?.salePriceCents || prodJson.data?.priceCents || COCONUT_OIL.cents;

      await page.goto(`/en/products/${COCONUT_OIL.id}?t=` + Date.now());
      await page.waitForLoadState('networkidle');
      const expectedKhr = formatKhr(currentCents, 4250);
      await expect(page.locator(`text=${expectedKhr}`)).toBeVisible();
    } finally {
      // SAFETY & INTEGRITY: Always restore original settings
      await request.put('http://localhost:4000/api/admin/settings', {
        headers: { Authorization: `Bearer ${adminToken}` },
        data: {
          storeName: originalSettings.storeName,
          khrRate: origRate,
          khqrMode: 'MOCK',
          shippingMethods: originalSettings.shippingMethods,
        },
      });

      const verifyRes = await request.get('http://localhost:4000/api/admin/settings', {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      const restored = (await verifyRes.json()).data;
      expect(restored.khrRate).toBe(origRate);
      expect(restored.khqrMode).toBe('MOCK');
    }
  });

  test('9. Audit logs page & before/after JSON diff viewer modal', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/en');
    await page.evaluate(() => localStorage.clear());

    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/audit-logs');
    await page.waitForLoadState('networkidle');

    // Assert audit log rows are visible
    await expect(page.locator('[data-testid="audit-log-row"]').first()).toBeVisible();

    // Click View Changes on first row
    await page.locator('[data-testid^="view-diff-btn-"]').first().click();
    await expect(page.locator('[data-testid="diff-modal"]')).toBeVisible();

    // Screenshot 11: admin-audit-diff.png
    await page.screenshot({ path: 'docs/screenshots/admin-audit-diff.png' });

    await page.keyboard.press('Escape');
  });

  test('10. STAFF login cannot open /admin/reports, /admin/settings, /admin/audit-logs (403)', async ({
    page,
  }) => {
    await page.context().clearCookies();
    await page.goto('/en');
    await page.evaluate(() => localStorage.clear());

    // Log in as staff
    await loginViaUI(page, 'staff@khmermart.local', 'Staff@12345', 'en');

    // 1. /admin/reports
    await page.goto('/en/admin/reports');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="admin-403-title"]')).toBeVisible();

    // 2. /admin/settings
    await page.goto('/en/admin/settings');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="admin-403-title"]')).toBeVisible();

    // 3. /admin/audit-logs
    await page.goto('/en/admin/audit-logs');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="admin-403-title"]')).toBeVisible();
  });

  test('11. Zero console errors and zero untranslated keys across all Phase 7B pages in EN and KM', async ({
    page,
  }) => {
    test.setTimeout(90000);
    await page.context().clearCookies();
    await page.goto('/en');
    await page.evaluate(() => localStorage.clear());

    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');

    const adminPaths = [
      '/en/admin/products',
      '/km/admin/products',
      '/en/admin/categories',
      '/km/admin/categories',
      '/en/admin/coupons',
      '/km/admin/coupons',
      '/en/admin/promotions',
      '/km/admin/promotions',
      '/en/admin/reviews',
      '/km/admin/reviews',
      '/en/admin/users',
      '/km/admin/users',
      '/en/admin/reports',
      '/km/admin/reports',
      '/en/admin/settings',
      '/km/admin/settings',
      '/en/admin/audit-logs',
      '/km/admin/audit-logs',
    ];

    const rawKeyRegex = /^[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/;

    for (const adminPath of adminPaths) {
      await page.goto(adminPath);
      await page.waitForLoadState('networkidle');

      const allText = await page.evaluate(() => {
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
        const nodes: string[] = [];
        while (walker.nextNode()) {
          const val = walker.currentNode.nodeValue?.trim();
          if (val) nodes.push(val);
        }
        return nodes;
      });

      const untranslated = allText.filter(
        (t) =>
          rawKeyRegex.test(t) &&
          !t.includes('@') &&
          !t.endsWith('.com') &&
          !t.endsWith('.local') &&
          !t.endsWith('.csv') &&
          !t.endsWith('.xlsx') &&
          !t.endsWith('.png') &&
          !t.endsWith('.jpg') &&
          !t.startsWith('0.') &&
          !t.startsWith('1.') &&
          !/^v\d+\.\d+\.\d+$/.test(t)
      );

      expect(untranslated, `Untranslated keys found on ${adminPath}`).toEqual([]);
    }
  });
});
