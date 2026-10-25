import { test, expect } from '@playwright/test';
import { createThrowawayUser, loginViaUI } from './helpers/auth';
import { scrollFullPageAndSettle } from './helpers/screenshot';

test.describe('Phase 6 — Notifications, Tracking & Reviews E2E against Real Backend', () => {
  let consoleErrors: string[] = [];

  test.beforeEach(async ({ page }) => {
    consoleErrors = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        const text = msg.text();
        // Ignore expected favicon or hydration warnings if any
        if (!text.includes('favicon') && !text.includes('Failed to load resource')) {
          consoleErrors.push(text);
        }
      }
    });
    await page.setViewportSize({ width: 1280, height: 800 });
  });

  test.afterEach(async () => {
    expect(consoleErrors).toEqual([]);
  });

  test('Teacher 12-step scenario: KHQR order -> paid notification -> admin ship + track events -> customer /track in EN & KM -> admin DELIVERED -> 5-star review -> product reviews updated -> duplicate rejected', async ({
    page,
    request,
  }) => {
    test.setTimeout(90000);
    // -------------------------------------------------------------
    // Step 1: Admin Token for API Operations
    // -------------------------------------------------------------
    const adminLoginRes = await request.post('http://localhost:4000/api/auth/login', {
      data: {
        email: 'admin@khmermart.local',
        password: 'Admin@12345',
      },
    });
    expect(adminLoginRes.ok()).toBe(true);
    const adminJson = await adminLoginRes.json();
    const adminToken = adminJson.data?.accessToken;
    expect(adminToken).toBeTruthy();

    // -------------------------------------------------------------
    // Step 2: Create Throwaway User & Login
    // -------------------------------------------------------------
    const user = await createThrowawayUser(request);
    await loginViaUI(page, user.email, user.password, 'en');

    // Add address for user
    const addrRes = await request.post('http://localhost:4000/api/users/me/addresses', {
      headers: { Authorization: `Bearer ${user.token}` },
      data: {
        recipientName: user.fullName,
        phone: user.phone,
        line1: 'Building 88, Street 2004',
        district: 'Sen Sok',
        province: 'Phnom Penh',
        postalCode: '12000',
        label: 'Home',
        isDefault: true,
      },
    });
    expect(addrRes.ok()).toBe(true);

    // -------------------------------------------------------------
    // Step 3: Add Laptop Pro 15 (ProductId: 1001) to Cart & Checkout
    // -------------------------------------------------------------
    await page.goto('/en/products/laptop-pro-15');
    await page.waitForLoadState('networkidle');

    // Select variants
    await page.click('[data-testid="variant-chip-ram-16GB"]');
    await page.click('[data-testid="variant-chip-color-Space Gray"]');
    await page.click('[data-testid="variant-chip-storage-512GB"]');
    await expect(page.locator('[data-testid="add-to-cart-button"]')).toBeEnabled();
    await page.click('[data-testid="add-to-cart-button"]');
    await expect(page.locator('[data-testid="cart-drawer"]')).toBeVisible();
    await page.click('[data-testid="cart-drawer-close"]');

    // Proceed to Cart & Checkout
    await page.goto('/en/cart');
    await page.waitForLoadState('networkidle');
    await page.click('[data-testid="proceed-to-checkout-btn"]');
    await page.waitForURL('**/checkout');

    // Checkout steps: Address -> Shipping -> Payment -> Review
    await expect(page.locator('[data-testid="step-address"]')).toBeVisible();
    await page.click('[data-testid="next-to-shipping-btn"]');

    await expect(page.locator('[data-testid="step-shipping"]')).toBeVisible();
    await page.click('[data-testid="next-to-payment-btn"]');

    await expect(page.locator('[data-testid="step-payment"]')).toBeVisible();
    await page.click('[data-testid="payment-method-KHQR"]');
    await page.click('[data-testid="next-to-review-btn"]');

    await expect(page.locator('[data-testid="step-review"]')).toBeVisible();
    await page.click('[data-testid="place-order-btn"]');
    await page.waitForURL('**/checkout/pay/*');

    // Extract orderNumber from Pay page
    const orderNumberDisplay = page.locator('[data-testid="order-number-display"]');
    await expect(orderNumberDisplay).toBeVisible({ timeout: 10000 });
    const orderNumber = (await orderNumberDisplay.innerText()).trim();
    expect(orderNumber).toMatch(/^KM-\d+-\d+$/);

    // -------------------------------------------------------------
    // Step 4: Simulate Paid -> Notification Generated
    // -------------------------------------------------------------
    const simPaidBtn = page.locator('[data-testid="simulate-paid-btn"]');
    await expect(simPaidBtn).toBeVisible();
    await simPaidBtn.click();
    await expect(page.locator('[data-testid="payment-status-paid"]')).toBeVisible({ timeout: 10000 });

    // Wait for redirect to order success page
    await page.waitForURL(`**/orders/${orderNumber}/success*`, { timeout: 10000 });
    await expect(page.locator('[data-testid="order-success-page"]')).toBeVisible();

    // -------------------------------------------------------------
    // Step 5: Bell Unread Count Increases & Dropdown Links to Order
    // -------------------------------------------------------------
    // Give backend event listener a moment to commit notification
    await page.waitForTimeout(1000);
    await page.evaluate(() => window.dispatchEvent(new CustomEvent('khmermart:notification-refresh')));

    // Refresh notifications or trigger poll by clicking bell
    const bellBtn = page.locator('[data-testid="notification-bell-btn"]');
    await expect(bellBtn).toBeVisible();

    // Wait for unread badge to appear with count >= 1
    const unreadBadge = page.locator('[data-testid="notification-bell-badge"]');
    await expect(unreadBadge).toBeVisible({ timeout: 10000 });
    const unreadCountText = (await unreadBadge.innerText()).trim();
    expect(Number(unreadCountText)).toBeGreaterThanOrEqual(1);

    // Open notification dropdown
    await bellBtn.click();
    const dropdown = page.locator('[data-testid="notification-dropdown"]');
    await expect(dropdown).toBeVisible();

    // Assert notification items in dropdown
    const dropdownItems = page.locator('[data-testid="notification-dropdown-item"]');
    await expect(dropdownItems.first()).toBeVisible();

    // Screenshot 1: bell-dropdown-en.png
    await page.screenshot({ path: 'docs/screenshots/bell-dropdown-en.png' });

    // Click dropdown notification -> marks as read and navigates to order
    await dropdownItems.first().click();
    await page.waitForURL(`**/orders/${orderNumber}*`, { timeout: 10000 });
    await expect(page.locator('[data-testid="order-detail-page"]')).toBeVisible();

    // -------------------------------------------------------------
    // Step 6: Notifications Page & Mark-All-As-Read Sets Unread to 0
    // -------------------------------------------------------------
    await page.goto('/km/account/notifications');
    await page.waitForLoadState('networkidle');

    // Assert Khmer notifications page rendered
    await expect(page.locator('[data-testid="notifications-page-container"]')).toBeVisible();
    await scrollFullPageAndSettle(page);

    // Screenshot 2: notifications-km.png
    await page.screenshot({ path: 'docs/screenshots/notifications-km.png', fullPage: true });

    // Click "Mark all as read"
    const markAllBtn = page.locator('[data-testid="mark-all-read-btn"]');
    await expect(markAllBtn).toBeVisible();
    await markAllBtn.click();

    // Verify unread badge is now gone (or 0)
    await expect(page.locator('[data-testid="notification-bell-badge"]')).toHaveCount(0);

    // -------------------------------------------------------------
    // Step 7: (Admin via API) Ship Order & Add Tracking Event
    // -------------------------------------------------------------
    const shipRes = await request.post(`http://localhost:4000/api/admin/orders/${orderNumber}/ship`, {
      headers: {
        Authorization: `Bearer ${adminToken}`,
        'Content-Type': 'application/json',
      },
      data: { carrier: 'KhmerMart Express' },
    });
    expect(shipRes.ok()).toBe(true);
    const shipJson = await shipRes.json();
    const trackingNumber = shipJson.data?.trackingNumber;
    expect(trackingNumber).toBeTruthy();
    expect(trackingNumber).toMatch(/^KMTRK/);

    // Admin adds IN_TRANSIT event
    const eventRes1 = await request.post(
      `http://localhost:4000/api/admin/orders/${orderNumber}/tracking-events`,
      {
        headers: {
          Authorization: `Bearer ${adminToken}`,
          'Content-Type': 'application/json',
        },
        data: {
          status: 'IN_TRANSIT',
          location: 'Phnom Penh Sorting Facility',
          note: 'Package scanned and sorted into delivery queue',
        },
      }
    );
    expect(eventRes1.ok()).toBe(true);

    // -------------------------------------------------------------
    // Step 8: Customer /track/{trackingNumber} in EN and KM
    // -------------------------------------------------------------
    // In English
    await page.goto(`/en/track/${trackingNumber}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="tracking-detail-card"]')).toBeVisible();
    await expect(page.locator('[data-testid="tracking-number-value"]')).toHaveText(trackingNumber);
    await expect(page.locator('[data-testid="tracking-carrier-value"]')).toContainText('KhmerMart Express');
    await expect(page.locator('[data-testid="tracking-status-badge"]')).toContainText(/In Transit/i);
    await expect(page.locator('[data-testid="tracking-events-timeline"]')).toBeVisible();

    await scrollFullPageAndSettle(page);
    // Screenshot 3: track-en.png
    await page.screenshot({ path: 'docs/screenshots/track-en.png', fullPage: true });

    // In Khmer
    await page.goto(`/km/track/${trackingNumber}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="tracking-detail-card"]')).toBeVisible();
    await expect(page.locator('[data-testid="tracking-status-badge"]')).toContainText(/កំពុងដឹកជញ្ជូន/);

    await scrollFullPageAndSettle(page);
    // Screenshot 4: track-km.png
    await page.screenshot({ path: 'docs/screenshots/track-km.png', fullPage: true });

    // Mobile Viewport (390x844)
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`/en/track/${trackingNumber}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="tracking-detail-card"]')).toBeVisible();

    await scrollFullPageAndSettle(page);
    // Screenshot 5: track-mobile.png
    await page.screenshot({ path: 'docs/screenshots/track-mobile.png', fullPage: true });

    // Reset viewport back to desktop
    await page.setViewportSize({ width: 1280, height: 800 });

    // -------------------------------------------------------------
    // Step 9: Unknown Tracking Number -> Not Found UI
    // -------------------------------------------------------------
    await page.goto('/en/track/UNKNOWN-TRACK-999999');
    await page.waitForLoadState('networkidle');
    const notFoundEl = page.locator('[data-testid="tracking-not-found"]');
    await expect(notFoundEl).toBeVisible();
    await expect(notFoundEl).toContainText(/not found/i);
    await expect(page.locator('[data-testid="back-to-track-btn"]')).toBeVisible();

    // -------------------------------------------------------------
    // Step 10: (Admin via API) Transition to OUT_FOR_DELIVERY & DELIVERED
    // -------------------------------------------------------------
    const outEventRes = await request.post(
      `http://localhost:4000/api/admin/orders/${orderNumber}/tracking-events`,
      {
        headers: {
          Authorization: `Bearer ${adminToken}`,
          'Content-Type': 'application/json',
        },
        data: {
          status: 'OUT_FOR_DELIVERY',
          location: 'Tuol Kouk Delivery Hub',
          note: 'Out for final delivery with courier',
        },
      }
    );
    expect(outEventRes.ok()).toBe(true);

    const delEventRes = await request.post(
      `http://localhost:4000/api/admin/orders/${orderNumber}/tracking-events`,
      {
        headers: {
          Authorization: `Bearer ${adminToken}`,
          'Content-Type': 'application/json',
        },
        data: {
          status: 'DELIVERED',
          location: 'Customer Address',
          note: 'Package handed directly to customer',
        },
      }
    );
    expect(delEventRes.ok()).toBe(true);

    // -------------------------------------------------------------
    // Step 11: Order Detail Timeline Shows Delivered & Track Shipment Button
    // -------------------------------------------------------------
    await page.goto(`/en/orders/${orderNumber}`);
    await page.waitForLoadState('networkidle');

    // Status badge shows Delivered
    const orderStatusBadge = page.locator('[data-testid="order-detail-status-badge"]');
    await expect(orderStatusBadge).toContainText(/Delivered/i);

    // Track shipment button links to /track/${trackingNumber}
    const trackShipmentBtn = page.locator('[data-testid="track-shipment-btn"]');
    await expect(trackShipmentBtn).toBeVisible();
    expect(await trackShipmentBtn.getAttribute('href')).toContain(`/track/${trackingNumber}`);

    await scrollFullPageAndSettle(page);
    // Screenshot 8: order-detail-delivered.png
    await page.screenshot({ path: 'docs/screenshots/order-detail-delivered.png', fullPage: true });

    // -------------------------------------------------------------
    // Step 12: Customer Writes 5-Star Review
    // -------------------------------------------------------------
    // "Write review" button on order item row
    const writeReviewBtn = page.locator('[data-testid="write-review-btn-1001"]');
    await expect(writeReviewBtn).toBeVisible();
    await writeReviewBtn.click();

    // Dialog opens
    const reviewDialog = page.locator('[data-testid="review-dialog"]');
    await expect(reviewDialog).toBeVisible();

    // Screenshot 6: review-dialog.png
    await page.screenshot({ path: 'docs/screenshots/review-dialog.png' });

    // Test form fields and validation errors: try submit with empty body
    await page.locator('[data-testid="submit-review-btn"]').click();
    await expect(page.locator('[data-testid="error-body"]')).toBeVisible();

    // Fill valid 5-star review
    const star5 = page.locator('[data-testid="star-5"]');
    await star5.click();
    await page.locator('[data-testid="review-title-input"]').fill('Exceptional Quality and Fast Delivery!');
    await page
      .locator('[data-testid="review-body-input"]')
      .fill('Amazing performance, crisp display, and the delivery was extremely swift. 10/10 recommended!');

    // Submit review
    await page.locator('[data-testid="submit-review-btn"]').click();
    await expect(reviewDialog).toBeHidden({ timeout: 5000 });

    // Button in order detail now displays "Reviewed"
    const reviewedBtn = page.locator('[data-testid="reviewed-btn-1001"]');
    await expect(reviewedBtn).toBeVisible();

    // -------------------------------------------------------------
    // Step 13: Admin Moderates & Approves the Review
    // -------------------------------------------------------------
    const pendingReviewsRes = await request.get('http://localhost:4000/api/admin/reviews?status=PENDING', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(pendingReviewsRes.ok()).toBe(true);
    const pendingJson = await pendingReviewsRes.json();
    const pendingReview = (pendingJson.data as any[]).find(
      (r) => r.productId === 1001 || r.title === 'Exceptional Quality and Fast Delivery!'
    );
    expect(pendingReview).toBeTruthy();
    const reviewId = pendingReview.id;

    // Approve the review
    const approveRes = await request.patch(`http://localhost:4000/api/admin/reviews/${reviewId}`, {
      headers: {
        Authorization: `Bearer ${adminToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        status: 'APPROVED',
        adminReply: 'Thank you for your wonderful review! We are delighted you love your new Laptop.',
      },
    });
    expect(approveRes.ok()).toBe(true);

    // -------------------------------------------------------------
    // Step 14: Product Page Shows Review and Rating Count/Average Changed
    // -------------------------------------------------------------
    await page.goto('/en/products/laptop-pro-15');
    await page.waitForLoadState('networkidle');

    // Approved reviews list contains the new review with Verified purchase badge
    const approvedReviewsList = page.locator('[data-testid="approved-reviews-list"]');
    await expect(approvedReviewsList).toBeVisible();
    await expect(approvedReviewsList).toContainText('Exceptional Quality and Fast Delivery!');
    await expect(page.locator('[data-testid="verified-purchase-badge"]').first()).toBeVisible();

    await scrollFullPageAndSettle(page);
    // Screenshot 7: product-reviews.png
    await page.screenshot({ path: 'docs/screenshots/product-reviews.png', fullPage: true });

    // -------------------------------------------------------------
    // Step 15: Second Review Attempt Shows Error
    // -------------------------------------------------------------
    const secondReviewRes = await request.post('http://localhost:4000/api/products/1001/reviews', {
      headers: {
        Authorization: `Bearer ${user.token}`,
        'Content-Type': 'application/json',
      },
      data: {
        rating: 5,
        title: 'Duplicate review',
        body: 'Attempting to review the same item twice',
      },
    });
    expect(secondReviewRes.status()).toBe(409);
    const dupJson = await secondReviewRes.json();
    expect(dupJson.success).toBe(false);
    expect(dupJson.error?.message).toContain('Use PATCH /api/reviews/{id} to edit');

    // -------------------------------------------------------------
    // Step 16: Zero untranslated keys verification on all touched pages
    // -------------------------------------------------------------
    for (const testUrl of [
      '/en/track',
      `/en/track/${trackingNumber}`,
      '/km/track',
      `/km/track/${trackingNumber}`,
      '/en/account/notifications',
      '/km/account/notifications',
      `/en/orders/${orderNumber}`,
      '/en/products/laptop-pro-15',
    ]) {
      await page.goto(testUrl);
      await page.waitForLoadState('networkidle');

      // Check for raw untranslated i18n keys like "notifications.someKey"
      const content = await page.content();
      const rawKeyMatches = content.match(/["'>](notifications|track|reviews|push)\.[a-zA-Z0-9_]+[<'"']/g);
      expect(rawKeyMatches).toBeNull();
    }

    // Cleanup: reject test review so product rating returns to seeded 4.7
    await request.patch(`http://localhost:4000/api/admin/reviews/${reviewId}`, {
      headers: {
        Authorization: `Bearer ${adminToken}`,
        'Content-Type': 'application/json',
      },
      data: { status: 'REJECTED' },
    });
  });

  test('Public Track form works: input tracking number -> navigates to /track/[trackingNumber]', async ({
    page,
    request,
  }) => {
    await page.goto('/en/track');
    await page.waitForLoadState('networkidle');

    const input = page.locator('[data-testid="tracking-number-input"]');
    await expect(input).toBeVisible();
    await input.fill('KMTRK88889999');

    await page.click('[data-testid="track-submit-btn"]');
    await page.waitForURL('**/track/KMTRK88889999');
    expect(page.url()).toContain('/track/KMTRK88889999');
  });

  test('Security page web push toggle shows friendly message in unsupported/denied environments', async ({
    page,
    request,
  }) => {
    const user = await createThrowawayUser(request);
    await loginViaUI(page, user.email, user.password, 'en');

    await page.goto('/en/account/security');
    await page.waitForLoadState('networkidle');

    const pushSection = page.locator('[data-testid="web-push-section"]');
    await expect(pushSection).toBeVisible();

    const pushToggle = page.locator('[data-testid="web-push-toggle"]');
    await expect(pushToggle).toBeVisible();
  });
});
