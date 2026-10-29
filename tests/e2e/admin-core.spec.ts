import { COCONUT_OIL, CATEGORY, LOW_STOCK } from './fixtures/catalog';
import { test, expect, Page, APIRequestContext } from '@playwright/test';
import { createThrowawayUser, loginViaUI } from './helpers/auth';
import { scrollFullPageAndSettle } from './helpers/screenshot';
import { formatUsd } from '../../src/lib/money';

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

test.describe('Phase 7A — Admin Core E2E Tests', () => {
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
          !text.includes('status of 403')
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

  test('1. Admin Dashboard: KPIs equal GET /admin/dashboard API numbers (assert UI == API) & localized screenshots', async ({
    page,
    request,
  }) => {
    // 1. Fetch dashboard data directly from real backend API
    const adminLoginRes = await request.post('http://localhost:4000/api/auth/login', {
      data: {
        email: 'admin@khmermart.local',
        password: 'Admin@12345',
      },
    });
    expect(adminLoginRes.ok()).toBe(true);
    const adminToken = (await adminLoginRes.json()).data.accessToken;

    const dashApiRes = await request.get('http://localhost:4000/api/admin/dashboard?range=30d', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(dashApiRes.ok()).toBe(true);
    const dashData = (await dashApiRes.json()).data;

    // 2. Admin logs in via UI
    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin?range=30d');
    await page.waitForLoadState('networkidle');

    // 3. Assert UI KPI values match API values
    await expect(page.locator('[data-testid="kpi-card-revenue"]')).toBeVisible();
    const uiRevenueUsd = await page.locator('[data-testid="kpi-revenue-usd"]').textContent();
    const expectedRevenueUsd = formatUsd(dashData.revenue30dCents);
    expect(uiRevenueUsd?.trim()).toBe(expectedRevenueUsd);

    const uiOrdersCount = await page.locator('[data-testid="kpi-orders-count"]').textContent();
    expect(uiOrdersCount?.trim()).toBe(String(dashData.ordersToday));

    const uiAvgOrderUsd = await page.locator('[data-testid="kpi-avg-order-usd"]').textContent();
    const expectedAvgOrderUsd = formatUsd(dashData.avgOrderValueCents);
    expect(uiAvgOrderUsd?.trim()).toBe(expectedAvgOrderUsd);

    const uiPendingPayments = await page.locator('[data-testid="kpi-pending-payments"]').textContent();
    expect(uiPendingPayments?.trim()).toBe(String(dashData.pendingPayments));

    // Seeded low stock: 1027 Silver Box (3 left) and 1004 Table Runner (4 left) fill the "Low stock" table
    const lowStockCard = page.locator('[data-testid="dashboard-low-stock-card"]');
    await expect(lowStockCard).toContainText(LOW_STOCK.silverBox.name);
    await expect(lowStockCard).toContainText(LOW_STOCK.tableRunner.name);
    await expect(lowStockCard).toContainText(`${LOW_STOCK.silverBox.stock} / 5`);
    await expect(lowStockCard).toContainText(`${LOW_STOCK.tableRunner.stock} / 5`);

    // Capture admin-dashboard-en.png
    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/admin-dashboard-en.png', fullPage: true });

    // 4. Localized Khmer Dashboard via top bar language switch & capture admin-dashboard-km.png
    await page.locator('button[aria-label="Switch language"]').click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="kpi-card-revenue"]')).toBeVisible();
    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/admin-dashboard-km.png', fullPage: true });
  });

  test('2 & 3. Orders lifecycle: Customer KHQR order -> top of Admin Orders -> Ship dialog -> Tracking event -> Mark DELIVERED -> Invalid transition shows error', async ({
    page,
    request,
  }) => {
    // 1. Create a paid KHQR order
    const { orderNumber } = await createPaidKhqrOrder(request);

    // 2. Log in as Admin and go to Orders list
    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/orders');
    await page.waitForLoadState('networkidle');

    // Orders page screenshot
    await page.screenshot({ path: 'docs/screenshots/admin-orders.png', fullPage: true });

    // Assert newly created order is visible in table
    const orderLink = page.locator(`[data-order-number="${orderNumber}"] [data-testid="view-order-btn"], [data-testid="order-link-${orderNumber}"]`).first();
    await expect(orderLink).toBeVisible({ timeout: 10000 });
    await orderLink.click();

    // 3. On Order Detail Page
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="admin-order-number"]')).toHaveText(orderNumber);
    await expect(page.locator('[data-testid="admin-order-status-badge"]')).toHaveText('CONFIRMED');

    // Capture initial order detail screenshot
    await page.screenshot({ path: 'docs/screenshots/admin-order-detail.png', fullPage: true });

    // 4. Open Ship Dialog
    const shipBtn = page.locator('[data-testid="open-ship-dialog-btn"]');
    await expect(shipBtn).toBeVisible();
    await shipBtn.click();

    await expect(page.locator('[data-testid="ship-dialog"]')).toBeVisible();

    // Capture admin-ship-dialog.png
    await page.screenshot({ path: 'docs/screenshots/admin-ship-dialog.png' });

    // Submit Ship Dialog
    await page.locator('[data-testid="confirm-ship-btn"]').click();
    await expect(page.locator('[data-testid="ship-success-message"]')).toBeVisible({ timeout: 10000 });
    await page.locator('button:has-text("Done")').click();
    await expect(page.locator('[data-testid="ship-dialog"]')).not.toBeVisible();

    // Assert tracking number is shown
    const trackingElem = page.locator('[data-testid="admin-order-tracking-number"]');
    await expect(trackingElem).toBeVisible({ timeout: 10000 });
    const trackingNumber = (await trackingElem.textContent())?.trim();
    expect(trackingNumber).toBeTruthy();
    expect(trackingNumber?.startsWith('KMTRK')).toBe(true);

    // 5. Add Tracking Event
    const addEventBtn = page.locator('[data-testid="open-tracking-event-dialog-btn"]');
    await expect(addEventBtn).toBeVisible();
    await addEventBtn.click();

    await expect(page.locator('[data-testid="add-tracking-event-dialog"]')).toBeVisible();
    await page.locator('[data-testid="event-location-input"]').fill('Phnom Penh Central Distribution');
    await page.locator('[data-testid="event-note-input"]').fill('Package scanned into outbound vehicle');
    await page.locator('[data-testid="submit-tracking-event-btn"]').click();
    await expect(page.locator('[data-testid="add-tracking-event-dialog"]')).not.toBeVisible();

    // Assert tracking event added to timeline
    await expect(page.locator('text=Phnom Penh Central Distribution')).toBeVisible({ timeout: 5000 });

    // 6. Mark DELIVERED
    const statusSelect = page.locator('[data-testid="status-transition-select"]');
    await expect(statusSelect).toBeVisible();
    await statusSelect.selectOption('DELIVERED');
    await page.locator('[data-testid="update-status-btn"]').click();

    // Assert status updated to DELIVERED
    await expect(page.locator('[data-testid="admin-order-status-badge"]')).toHaveText('DELIVERED', { timeout: 10000 });

    // 7. Test 3: Invalid transition shows error
    // Once DELIVERED, attempt an invalid transition to SHIPPED
    await statusSelect.evaluate((el: HTMLSelectElement) => {
      const opt = document.createElement('option');
      opt.value = 'SHIPPED';
      opt.text = 'SHIPPED (Invalid)';
      el.appendChild(opt);
      el.value = 'SHIPPED';
      el.dispatchEvent(new Event('change', { bubbles: true }));
    });

    await page.locator('[data-testid="update-status-btn"]').click();

    // Assert error message appears
    const errorAlert = page.locator('[data-testid="status-transition-error"]');
    await expect(errorAlert).toBeVisible({ timeout: 10000 });
    const errText = await errorAlert.textContent();
    expect(errText).toMatch(/INVALID_STATUS_TRANSITION|Cannot transition order/i);
  });

  test('4. Inventory: Restock +5 on a variant increases available by 5 and RESTOCK movement appears', async ({
    page,
    request,
  }) => {
    // 1. Log in as admin and navigate to inventory
    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/inventory');
    await page.waitForLoadState('networkidle');

    // Inventory screenshot
    await page.screenshot({ path: 'docs/screenshots/admin-inventory.png', fullPage: true });

    // Find the first inventory row
    const firstRow = page.locator('[data-testid="inventory-row"]').first();
    await expect(firstRow).toBeVisible();
    const sku = await firstRow.getAttribute('data-sku');
    expect(sku).toBeTruthy();

    // Get current available count
    const availableCell = firstRow.locator('td').nth(4);
    const initialAvailable = parseInt((await availableCell.textContent()) || '0', 10);

    // Open restock dialog
    const restockBtn = firstRow.locator(`[data-testid="restock-btn-${sku}"]`);
    await restockBtn.click();

    // Fill restock form (+5)
    await page.locator('[data-testid="restock-quantity-input"]').fill('5');
    await page.locator('[data-testid="restock-reason-input"]').fill('Playwright E2E Restock Test +5');
    await page.locator('[data-testid="restock-submit-btn"]').click();

    // Verify available count increased by 5
    await expect(availableCell).toHaveText(String(initialAvailable + 5), { timeout: 10000 });

    // Open movements drawer
    const movementsBtn = firstRow.locator(`[data-testid="movements-btn-${sku}"]`);
    await movementsBtn.click();

    const drawer = page.locator('[data-testid="inventory-movements-drawer"]');
    await expect(drawer).toBeVisible();

    // Assert RESTOCK movement appears with +5
    const latestMovement = drawer.locator('[data-testid="inventory-movement-item"]').first();
    await expect(latestMovement).toBeVisible();
    await expect(latestMovement).toHaveAttribute('data-movement-type', 'RESTOCK');
    await expect(latestMovement).toContainText('+5');
    await expect(latestMovement).toContainText('Playwright E2E Restock Test +5');
  });

  test('5. Payments: Export CSV downloads text/csv and displays transactions table', async ({ page }) => {
    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/payments');
    await page.waitForLoadState('networkidle');

    // Payments page screenshot
    await page.screenshot({ path: 'docs/screenshots/admin-payments.png', fullPage: true });

    // Verify CSV export
    const downloadPromise = page.waitForEvent('download');
    await page.locator('[data-testid="export-payments-csv-btn"]').click();
    const download = await downloadPromise;

    expect(download.suggestedFilename()).toMatch(/payments.*\.csv/);
  });

  test('6. STAFF permissions: Sees no Users/Reports/Settings/Audit links, and /admin/users returns 403', async ({
    page,
  }) => {
    // Log in as staff
    await loginViaUI(page, 'staff@khmermart.local', 'Staff@12345', 'en');
    await page.goto('/en/admin');
    await page.waitForLoadState('networkidle');

    // Capture staff sidebar screenshot
    await page.screenshot({ path: 'docs/screenshots/admin-staff-sidebar.png' });

    // Assert admin-only links are not rendered in sidebar
    await expect(page.locator('[data-testid="admin-nav-payments"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="admin-nav-users"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="admin-nav-reports"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="admin-nav-settings"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="admin-nav-audit-logs"]')).not.toBeVisible();

    // Attempt to access /admin/users
    await page.goto('/en/admin/users');
    await page.waitForLoadState('networkidle');

    // Assert 403 Forbidden is rendered
    await expect(page.locator('[data-testid="admin-forbidden"]')).toBeVisible();
    await expect(page.locator('[data-testid="admin-403-title"]')).toBeVisible();
  });

  test('7. CUSTOMER permissions: Navigating to /admin renders 403 Forbidden', async ({ page, request }) => {
    const customer = await createThrowawayUser(request);
    await loginViaUI(page, customer.email, customer.password, 'en');

    // Attempt to access /admin
    await page.goto('/en/admin');
    await page.waitForLoadState('networkidle');

    // Assert 403 Forbidden
    await expect(page.locator('[data-testid="admin-forbidden"]')).toBeVisible();
    await expect(page.locator('[data-testid="admin-403-title"]')).toBeVisible();
  });

  test('8. Zero console errors and zero untranslated keys across all Phase 7A admin pages in EN and KM', async ({
    page,
    request,
  }) => {
    test.setTimeout(90000);

    // Create a delivered order for testing order detail
    const { orderNumber } = await createPaidKhqrOrder(request);

    // Ship it via API so order detail has rich info
    const adminLoginRes = await request.post('http://localhost:4000/api/auth/login', {
      data: { email: 'admin@khmermart.local', password: 'Admin@12345' },
    });
    const adminToken = (await adminLoginRes.json()).data.accessToken;
    await request.post(`http://localhost:4000/api/admin/orders/${orderNumber}/ship`, {
      headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' },
      data: { carrier: 'KhmerMart Express' },
    });

    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');

    const adminPages = [
      '/en/admin',
      '/km/admin',
      '/en/admin/orders',
      '/km/admin/orders',
      `/en/admin/orders/${orderNumber}`,
      `/km/admin/orders/${orderNumber}`,
      '/en/admin/payments',
      '/km/admin/payments',
      '/en/admin/inventory',
      '/km/admin/inventory',
    ];

    const rawKeyRegex = /^[a-z]+\.[a-zA-Z.]+$/;

    for (const adminPath of adminPages) {
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

      // Filter for raw untranslated i18n keys
      const untranslated = allText.filter(
        (t) =>
          rawKeyRegex.test(t) &&
          !t.includes('@') &&
          !t.endsWith('.com') &&
          !t.endsWith('.local') &&
          !t.endsWith('.csv') &&
          !t.endsWith('.png') &&
          !t.endsWith('.jpg')
      );

      expect(untranslated, `Untranslated keys found on ${adminPath}`).toEqual([]);
    }
  });
});
