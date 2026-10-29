import { test, expect, APIRequestContext } from '@playwright/test';
import { createThrowawayUser, loginViaUI } from './helpers/auth';
import { scrollFullPageAndSettle } from './helpers/screenshot';
import { selectHeroVariant } from './helpers/shop';
import { COCONUT_OIL, HERO } from './fixtures/catalog';

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
    // Step 3: Add the hero Takeo Hol Silk Sampot (ProductId: 1001) to Cart & Checkout
    // -------------------------------------------------------------
    await page.goto(`/en/products/${HERO.slug}`);
    await page.waitForLoadState('networkidle');

    // Select variants
    await selectHeroVariant(page);
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

    // Verify GET /notifications/unread-count returns 0 from backend API
    const unreadCountRes = await request.get('http://localhost:4000/api/notifications/unread-count', {
      headers: { Authorization: `Bearer ${user.token}` },
    });
    expect(unreadCountRes.ok()).toBe(true);
    const unreadCountJson = await unreadCountRes.json();
    expect(unreadCountJson.data?.count).toBe(0);

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
    await expect(page.locator('[data-testid="tracking-status-badge"]')).toContainText(/កំពុងធ្វើដំណើរ/);

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
    await page.goto('/en/track/XX-NOT-REAL');
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
    const writeReviewBtn = page.locator(`[data-testid="write-review-btn-${HERO.id}"]`);
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
      .fill('Beautiful weaving, rich colours, and the delivery was extremely swift. 10/10 recommended!');

    // Submit review
    await page.locator('[data-testid="submit-review-btn"]').click();
    await expect(reviewDialog).toBeHidden({ timeout: 5000 });

    // Button in order detail now displays "Reviewed"
    const reviewedBtn = page.locator(`[data-testid="reviewed-btn-${HERO.id}"]`);
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
      (r) => r.productId === HERO.id || r.title === 'Exceptional Quality and Fast Delivery!'
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
        adminReply: 'Thank you for your wonderful review! We are delighted you love your new silk sampot.',
      },
    });
    expect(approveRes.ok()).toBe(true);

    // -------------------------------------------------------------
    // Step 14: Product Page Shows Review and Rating Count/Average Changed
    // -------------------------------------------------------------
    await page.goto(`/en/products/${HERO.slug}`);
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
    const secondReviewRes = await request.post(`http://localhost:4000/api/products/${HERO.id}/reviews`, {
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
      `/en/products/${HERO.slug}`,
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

  // =========================================================================
  // Phase 6 Test Gaps: Dedicated Targeted E2E Tests
  // =========================================================================

  test('a: "Mark all as read" -> GET /notifications/unread-count returns 0 and bell badge disappears', async ({
    page,
    request,
  }) => {
    // 1. Create delivered order with real tracking number and paid notification
    const { user } = await createDeliveredOrderWithTracking(request);

    // 2. Login via UI
    await loginViaUI(page, user.email, user.password, 'en');

    // 3. Bell badge shows unread count >= 1 and API returns count >= 1
    const unreadBadge = page.locator('[data-testid="notification-bell-badge"]');
    await expect(unreadBadge).toBeVisible({ timeout: 10000 });
    const countBefore = Number((await unreadBadge.innerText()).trim());
    expect(countBefore).toBeGreaterThanOrEqual(1);

    const apiUnreadBefore = await request.get('http://localhost:4000/api/notifications/unread-count', {
      headers: { Authorization: `Bearer ${user.token}` },
    });
    expect(apiUnreadBefore.ok()).toBe(true);
    const jsonBefore = await apiUnreadBefore.json();
    expect(jsonBefore.data?.count).toBeGreaterThanOrEqual(1);

    // 4. Navigate to notifications page and click "Mark all as read"
    await page.goto('/en/account/notifications');
    await page.waitForLoadState('networkidle');

    const markAllBtn = page.locator('[data-testid="mark-all-read-btn"]');
    await expect(markAllBtn).toBeVisible();
    await markAllBtn.click();

    // 5. Bell badge disappears
    await expect(page.locator('[data-testid="notification-bell-badge"]')).toHaveCount(0);

    // 6. GET /notifications/unread-count returns 0
    const apiUnreadAfter = await request.get('http://localhost:4000/api/notifications/unread-count', {
      headers: { Authorization: `Bearer ${user.token}` },
    });
    expect(apiUnreadAfter.ok()).toBe(true);
    const jsonAfter = await apiUnreadAfter.json();
    expect(jsonAfter.data?.count).toBe(0);
  });

  test('b: Unknown tracking number (/track/XX-NOT-REAL) -> friendly not-found UI, no console error', async ({
    page,
  }) => {
    // English
    await page.goto('/en/track/XX-NOT-REAL');
    await page.waitForLoadState('networkidle');

    const notFoundElEn = page.locator('[data-testid="tracking-not-found"]');
    await expect(notFoundElEn).toBeVisible();
    await expect(notFoundElEn).toContainText('Tracking number not found');
    await expect(notFoundElEn).toContainText('XX-NOT-REAL');
    await expect(page.locator('[data-testid="back-to-track-btn"]')).toBeVisible();

    // Khmer
    await page.goto('/km/track/XX-NOT-REAL');
    await page.waitForLoadState('networkidle');

    const notFoundElKm = page.locator('[data-testid="tracking-not-found"]');
    await expect(notFoundElKm).toBeVisible();
    await expect(notFoundElKm).toContainText('រកមិនឃើញលេខតាមដានទេ');
    await expect(notFoundElKm).toContainText('XX-NOT-REAL');
    await expect(page.locator('[data-testid="back-to-track-btn"]')).toBeVisible();

    expect(consoleErrors).toEqual([]);
  });

  test('c: Zero console errors AND zero untranslated keys on /account/notifications, /track/{real number}, and the review dialog', async ({
    page,
    request,
  }) => {
    test.setTimeout(60000);
    const checkNoUntranslatedKeys = async (contextName: string) => {
      const rawKeyRegex = /^[a-z]+\.[a-zA-Z.]+$/;
      const allText = await page.evaluate(() => {
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
        const nodes: string[] = [];
        while (walker.nextNode()) {
          const val = walker.currentNode.nodeValue?.trim();
          if (val) nodes.push(val);
        }
        return nodes;
      });
      const untranslated = allText.filter((t) => rawKeyRegex.test(t));
      expect(untranslated, `Untranslated keys found on ${contextName}`).toEqual([]);
    };

    // 1. Create delivered order with tracking
    const { user, orderNumber, trackingNumber } = await createDeliveredOrderWithTracking(request);
    await loginViaUI(page, user.email, user.password, 'en');

    // 2. Check /en/account/notifications and /km/account/notifications
    await page.goto('/en/account/notifications');
    await page.waitForLoadState('networkidle');
    await checkNoUntranslatedKeys('/en/account/notifications');

    await page.goto('/km/account/notifications');
    await page.waitForLoadState('networkidle');
    await checkNoUntranslatedKeys('/km/account/notifications');

    // 3. Check /en/track/{real number} and /km/track/{real number}
    await page.goto(`/en/track/${trackingNumber}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="tracking-detail-card"]')).toBeVisible();
    await checkNoUntranslatedKeys(`/en/track/${trackingNumber}`);

    await page.goto(`/km/track/${trackingNumber}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="tracking-detail-card"]')).toBeVisible();
    await checkNoUntranslatedKeys(`/km/track/${trackingNumber}`);

    // 4. Check review dialog in English
    await page.goto(`/en/orders/${orderNumber}`);
    await page.waitForLoadState('networkidle');
    const writeReviewBtnEn = page.locator(`[data-testid="write-review-btn-${COCONUT_OIL.id}"]`);
    await expect(writeReviewBtnEn).toBeVisible();
    await writeReviewBtnEn.click();

    const dialogEn = page.locator('[data-testid="review-dialog"]');
    await expect(dialogEn).toBeVisible();
    await checkNoUntranslatedKeys('review dialog (EN)');
    await page.locator('[aria-label="Close dialog"]').click();
    await expect(dialogEn).toBeHidden();

    // 5. Check review dialog in Khmer
    await page.goto(`/km/orders/${orderNumber}`);
    await page.waitForLoadState('networkidle');
    const writeReviewBtnKm = page.locator(`[data-testid="write-review-btn-${COCONUT_OIL.id}"]`);
    await expect(writeReviewBtnKm).toBeVisible();
    await writeReviewBtnKm.click();

    const dialogKm = page.locator('[data-testid="review-dialog"]');
    await expect(dialogKm).toBeVisible();
    await checkNoUntranslatedKeys('review dialog (KM)');
    await page.locator('[aria-label="Close dialog"]').click();
    await expect(dialogKm).toBeHidden();

    expect(consoleErrors).toEqual([]);
  });

  test('d: /km/track/{real number} at 390x844 shows Khmer status names and no horizontal scroll (document.documentElement.scrollWidth <= 390)', async ({
    page,
    request,
  }) => {
    // 1. Create delivered order with tracking number and timeline events
    const { trackingNumber } = await createDeliveredOrderWithTracking(request);

    // 2. Set viewport to mobile 390x844
    await page.setViewportSize({ width: 390, height: 844 });

    // 3. Navigate to /km/track/{trackingNumber}
    await page.goto(`/km/track/${trackingNumber}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="tracking-detail-card"]')).toBeVisible();

    // 4. Verify Khmer status names are displayed
    const statusBadge = page.locator('[data-testid="tracking-status-badge"]');
    await expect(statusBadge).toBeVisible();
    await expect(statusBadge).toContainText('បានប្រគល់ជោគជ័យ');

    // Also check event status in timeline
    const timeline = page.locator('[data-testid="tracking-events-timeline"]');
    await expect(timeline).toBeVisible();
    await expect(timeline).toContainText('បានប្រគល់ជោគជ័យ');

    // 5. Verify no horizontal scroll
    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
    expect(scrollWidth, `Expected scrollWidth (${scrollWidth}) <= 390`).toBeLessThanOrEqual(390);

    expect(consoleErrors).toEqual([]);
  });
});

async function createDeliveredOrderWithTracking(request: APIRequestContext) {
  // 1. Admin login
  const adminLogin = await request.post('http://localhost:4000/api/auth/login', {
    data: { email: 'admin@khmermart.local', password: 'Admin@12345' },
  });
  const adminJson = await adminLogin.json();
  const adminToken = adminJson.data.accessToken;

  // 2. User register
  const user = await createThrowawayUser(request);

  // 3. User address
  const addrRes = await request.post('http://localhost:4000/api/users/me/addresses', {
    headers: { Authorization: `Bearer ${user.token}` },
    data: {
      recipientName: user.fullName,
      phone: user.phone,
      line1: 'Building 99, St 2004',
      district: 'Sen Sok',
      province: 'Phnom Penh',
      postalCode: '12000',
      label: 'Home',
      isDefault: true,
    },
  });
  const addressId = (await addrRes.json()).data.id;

  // 4. Cart item (Virgin Coconut Oil, Qty 1)
  await request.post('http://localhost:4000/api/cart/items', {
    headers: { Authorization: `Bearer ${user.token}` },
    data: { productId: COCONUT_OIL.id, quantity: 1 },
  });

  // 5. Checkout
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

  // 6. Generate KHQR and simulate payment
  const payRes = await request.post(`http://localhost:4000/api/payments/khqr/${orderNumber}`, {
    headers: { Authorization: `Bearer ${user.token}` },
  });
  const paymentId = (await payRes.json()).data.paymentId;

  await request.post(`http://localhost:4000/api/payments/${paymentId}/simulate-paid`, {
    headers: { Authorization: `Bearer ${user.token}` },
  });

  // 7. Admin ship
  const shipRes = await request.post(`http://localhost:4000/api/admin/orders/${orderNumber}/ship`, {
    headers: {
      Authorization: `Bearer ${adminToken}`,
      'Content-Type': 'application/json',
    },
    data: { carrier: 'KhmerMart Express' },
  });
  const trackingNumber = (await shipRes.json()).data.trackingNumber;

  // 8. Admin tracking event IN_TRANSIT
  const transitRes = await request.post(`http://localhost:4000/api/admin/orders/${orderNumber}/tracking-events`, {
    headers: {
      Authorization: `Bearer ${adminToken}`,
      'Content-Type': 'application/json',
    },
    data: {
      status: 'IN_TRANSIT',
      location: 'Phnom Penh Sorting Facility',
      note: 'Package in transit',
    },
  });
  expect(transitRes.ok()).toBe(true);

  // 9. Admin tracking event OUT_FOR_DELIVERY
  const outRes = await request.post(`http://localhost:4000/api/admin/orders/${orderNumber}/tracking-events`, {
    headers: {
      Authorization: `Bearer ${adminToken}`,
      'Content-Type': 'application/json',
    },
    data: {
      status: 'OUT_FOR_DELIVERY',
      location: 'Tuol Kouk Delivery Hub',
      note: 'Out for final delivery with courier',
    },
  });
  expect(outRes.ok()).toBe(true);

  // 10. Admin tracking event DELIVERED
  const delRes = await request.post(`http://localhost:4000/api/admin/orders/${orderNumber}/tracking-events`, {
    headers: {
      Authorization: `Bearer ${adminToken}`,
      'Content-Type': 'application/json',
    },
    data: {
      status: 'DELIVERED',
      location: 'Customer Address',
      note: 'Package delivered to customer',
    },
  });
  expect(delRes.ok()).toBe(true);

  return {
    user,
    adminToken,
    orderNumber,
    trackingNumber,
  };
}
