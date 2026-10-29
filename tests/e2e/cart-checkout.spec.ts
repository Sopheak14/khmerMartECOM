import { test, expect } from '@playwright/test';
import { createThrowawayUser, loginViaUI } from './helpers/auth';
import { scrollFullPageAndSettle } from './helpers/screenshot';
import { selectHeroVariant } from './helpers/shop';
import { API, COUPON, HERO, RICE, usd } from './fixtures/catalog';

test.describe('Phase 4 — Cart, Promotions, Checkout, Shipping E2E against Real Backend', () => {

  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
  });

  test('1: Guest cart: variant required, button disabled until full combination, drawer opens, reload persists km_guest', async ({ page }) => {
    // 1. Visit the hero product
    await page.goto(`/en/products/${HERO.slug}`);
    await expect(page.locator('[data-testid="product-detail-title"]')).toHaveText(new RegExp(HERO.name, 'i'));

    // Verify Add to Cart button is initially disabled because no full variant combination is chosen
    const addToCartBtn = page.locator('[data-testid="add-to-cart-button"]');
    await expect(addToCartBtn).toBeDisabled();
    await expect(addToCartBtn).toHaveText(/Choose a variant first/i);

    // Select only the color (partial combination) -> button still disabled
    await page.click(`[data-testid="variant-chip-color-${HERO.color}"]`);
    await expect(addToCartBtn).toBeDisabled();

    // Select the grade (full combination: Ruby Red / Standard)
    await page.click('[data-testid="variant-chip-grade-Standard"]');

    // Button is now enabled!
    await expect(addToCartBtn).toBeEnabled();
    await expect(addToCartBtn).toHaveText(/Add to Cart/i);

    // Click Add to Cart
    await addToCartBtn.click();

    // Verify Cart Drawer opens automatically
    const drawer = page.locator('[data-testid="cart-drawer"]');
    await expect(drawer).toBeVisible({ timeout: 5000 });
    await expect(drawer.locator('[data-testid="cart-drawer-item"]').first()).toBeVisible();
    await expect(drawer).toContainText(HERO.name);

    // Verify Header cart count is updated to 1
    const headerCount = page.locator('[data-testid="header-cart-count"]');
    await expect(headerCount).toHaveText('1');

    // Close drawer
    await page.click('[data-testid="cart-drawer-close"]');
    await expect(drawer).not.toBeVisible();

    // Reload the page: guest cart must persist via km_guest cookie
    await page.reload();
    await page.waitForLoadState('networkidle');

    // Header count is still 1
    await expect(page.locator('[data-testid="header-cart-count"]')).toHaveText('1');

    // Open cart drawer from header
    await page.click('[data-testid="header-cart-button"]');
    await expect(drawer).toBeVisible();
    await expect(drawer).toContainText(HERO.name);
  });

  test('2: Cart page: quantity stepper increase/decrease, remove item with confirmation, Khmer empty state', async ({ page }) => {
    // 1. Visit /en/cart (cart has the hero product from previous test or add one)
    await page.goto('/en/cart');
    await page.waitForLoadState('networkidle');

    // If empty, add item via detail page
    const cartItems = page.locator('[data-testid="cart-item"]');
    if ((await cartItems.count()) === 0) {
      await page.goto(`/en/products/${HERO.slug}`);
      await page.waitForLoadState('networkidle');
      await expect(page.locator('[data-testid="product-detail-title"]')).toHaveText(new RegExp(HERO.name, 'i'));
      await selectHeroVariant(page);
      const addBtn = page.locator('[data-testid="add-to-cart-button"]');
      await expect(addBtn).toBeEnabled();
      await addBtn.click();
      await expect(page.locator('[data-testid="cart-drawer"]')).toBeVisible();
      await page.goto('/en/cart');
    }

    await expect(page.locator('[data-testid="cart-items-list"]')).toBeVisible();

    // Stepper checks
    const qtyValue = page.locator('[data-testid="qty-value"]').first();
    const qtyPlus = page.locator('[data-testid="qty-plus"]').first();
    const qtyMinus = page.locator('[data-testid="qty-minus"]').first();

    const initialQty = Number(await qtyValue.innerText());
    expect(initialQty).toBeGreaterThanOrEqual(1);

    // Increase qty
    await qtyPlus.click();
    await expect(qtyValue).toHaveText(String(initialQty + 1));

    // Decrease qty back
    await qtyMinus.click();
    await expect(qtyValue).toHaveText(String(initialQty));

    // Take screenshot: cart-en.png
    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/cart-en.png', fullPage: true });

    // Test in Khmer: /km/cart
    await page.goto('/km/cart');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('h1')).toHaveText(/កន្ត្រកទំនិញ/i);

    // Test removal of item with confirmation
    const removeBtn = page.locator('[data-testid="remove-cart-item"]').first();
    await removeBtn.click();

    // Confirmation button appears
    const confirmBtn = page.locator('[data-testid="confirm-remove-btn"]');
    await expect(confirmBtn).toBeVisible();
    await confirmBtn.click();

    // Wait for item to be removed
    await expect(page.locator('[data-testid="cart-empty-state"]')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('[data-testid="cart-empty-state"]')).toContainText(/កន្ត្រកទំនិញរបស់អ្នកទទេ/i);
  });

  test('2b: Cart stepper at max stock shows OUT_OF_STOCK message in EN and KM', async ({ page, context }) => {
    await context.clearCookies();
    await page.goto(`/en/products/${HERO.slug}`);
    await page.waitForLoadState('networkidle');
    await selectHeroVariant(page);
    await expect(page.locator('[data-testid="add-to-cart-button"]')).toBeEnabled();
    await page.click('[data-testid="add-to-cart-button"]');
    await expect(page.locator('[data-testid="cart-drawer"]')).toBeVisible();

    // Raise the line to its full available stock through the real API, then press "+" once more
    const available = await page.evaluate(async (api) => {
      const cart = (await (await fetch(`${api}/cart`, { credentials: 'include' })).json()).data;
      const item = cart.items[0];
      await fetch(`${api}/cart/items/${item.id}`, {
        method: 'PATCH',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ quantity: item.available }),
      });
      return item.available as number;
    }, API);

    for (const [locale, expected] of [
      ['en', `Only ${available} left in stock (OUT_OF_STOCK)`],
      ['km', `នៅសល់តែ ${available} ទៀតប៉ុណ្ណោះក្នុងស្តុក (OUT_OF_STOCK)`],
    ] as const) {
      await page.goto(`/${locale}/cart`);
      await page.waitForLoadState('networkidle');
      await expect(page.locator('[data-testid="qty-value"]').first()).toHaveText(String(available));
      await page.click('[data-testid="qty-plus"]');
      await expect(page.locator('[data-testid="stock-error"]')).toHaveText(expected);
    }
  });

  test('3: Coupons (SAVE5, WELCOME10, FREESHIP, invalid) and promotion lines in CartDTO', async ({ page }) => {
    // 1. Add Phka Rumduol Jasmine Rice 5 kg ($12.00 subtotal < $35)
    await page.goto(`/en/products/${RICE.slug}`);
    await page.click('[data-testid="variant-chip-weight-5 kg"]');
    await page.click('[data-testid="add-to-cart-button"]');
    await page.click('[data-testid="cart-drawer-close"]');

    await page.goto('/en/cart');
    await expect(page.locator('[data-testid="cart-item"]')).toHaveCount(1);

    // Cart subtotal is $12.00 (< $35.00). Try SAVE5:
    const couponInput = page.locator('[data-testid="coupon-input"]');
    const applyBtn = page.locator('[data-testid="apply-coupon-btn"]');

    await couponInput.fill('SAVE5');
    await applyBtn.click();

    // Fails with COUPON_MIN_ORDER
    const couponError = page.locator('[data-testid="coupon-error"]');
    await expect(couponError).toBeVisible();
    await expect(couponError).toContainText(usd(COUPON.save5MinOrderCents));

    // Now add the hero silk sampot to make cart subtotal >= $35
    await page.goto(`/en/products/${HERO.slug}`);
    await selectHeroVariant(page);
    await page.click('[data-testid="add-to-cart-button"]');
    await page.click('[data-testid="cart-drawer-close"]');

    await page.goto('/en/cart');
    await expect(page.locator('[data-testid="cart-item"]')).toHaveCount(2);

    // Now apply SAVE5 on cart >= $35
    await couponInput.fill('SAVE5');
    await applyBtn.click();

    // Verify applied badge appears
    const appliedBadge = page.locator('[data-testid="applied-coupon-badge"]');
    await expect(appliedBadge).toBeVisible();
    await expect(appliedBadge).toContainText('SAVE5');

    // Verify coupon line displays net coupon discount -$5.00
    const couponLineAmount = page.locator('[data-testid="coupon-discount-amount"]');
    await expect(couponLineAmount).toBeVisible();
    await expect(couponLineAmount).toHaveText('-$5.00');

    // Fetch and log CartDTO numbers
    const cartRes = await page.evaluate(async (api) => {
      const res = await fetch(`${api}/cart`, { credentials: 'include' });
      return await res.json();
    }, API);
    console.log('CART DTO WITH SAVE5:', JSON.stringify(cartRes.data, null, 2));
    expect(cartRes.data.coupon.code).toBe('SAVE5');
    expect(cartRes.data.discountCents).toBeGreaterThanOrEqual(500);

    // Remove coupon
    await page.click('[data-testid="remove-coupon-btn"]');
    await expect(appliedBadge).not.toBeVisible();

    // Apply WELCOME10 (10% capped at $20.00)
    await couponInput.fill('WELCOME10');
    await applyBtn.click();
    await expect(appliedBadge).toBeVisible();
    await expect(appliedBadge).toContainText('WELCOME10');

    const cartWelcome = await page.evaluate(async (api) => {
      const res = await fetch(`${api}/cart`, { credentials: 'include' });
      return await res.json();
    }, API);
    console.log('CART DTO WITH WELCOME10:', JSON.stringify(cartWelcome.data, null, 2));
    expect(cartWelcome.data.coupon.code).toBe('WELCOME10');
    // Verify capped at $20 (2000 cents) plus promotions
    expect(cartWelcome.data.discountCents).toBeGreaterThanOrEqual(2000);

    // Remove coupon
    await page.click('[data-testid="remove-coupon-btn"]');

    // Apply FREESHIP
    await couponInput.fill('FREESHIP');
    await applyBtn.click();
    await expect(appliedBadge).toBeVisible();

    const cartFreeShip = await page.evaluate(async (api) => {
      const res = await fetch(`${api}/cart`, { credentials: 'include' });
      return await res.json();
    }, API);
    console.log('CART DTO WITH FREESHIP:', JSON.stringify(cartFreeShip.data, null, 2));
    expect(cartFreeShip.data.coupon.code).toBe('FREESHIP');
    expect(cartFreeShip.data.shippingEstimateCents).toBe(0);

    // Verify promotion discounts line items
    expect(cartFreeShip.data.promotionDiscounts.length).toBeGreaterThan(0);
    console.log('CART PROMOTION DISCOUNTS:', cartFreeShip.data.promotionDiscounts);

    // Test unknown coupon code -> COUPON_INVALID
    await page.click('[data-testid="remove-coupon-btn"]');
    await couponInput.fill('UNKNOWN_INVALID_COUPON_999');
    await applyBtn.click();
    await expect(page.locator('[data-testid="coupon-error"]')).toContainText(/Coupon code is invalid/i);

    // Capture screenshot: cart-km-coupon.png in Khmer
    await page.goto('/km/cart');
    await page.waitForLoadState('networkidle');
    await page.fill('[data-testid="coupon-input"]', 'SAVE5');
    await page.click('[data-testid="apply-coupon-btn"]');
    await expect(page.locator('[data-testid="applied-coupon-badge"]')).toBeVisible();

    // Verify promotion discount label is translated in Khmer
    const kmPromo = page.locator('[data-testid="promotion-discount-item"]').first();
    await expect(kmPromo).toBeVisible();
    await expect(kmPromo).toContainText(/ការបញ្ចុះតម្លៃ/i);

    // Verify coupon line displays net coupon discount -$5.00
    await expect(page.locator('[data-testid="coupon-discount-amount"]')).toHaveText('-$5.00');

    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/cart-km-coupon.png', fullPage: true });
  });

  test('4: Guest cart merge on login: POST /api/cart/merge transfers items to user cart and clears km_guest', async ({ page, request, context }) => {
    // 1. In a clean guest session, add an item
    await context.clearCookies();
    await page.goto(`/en/products/${HERO.slug}`);
    await page.waitForLoadState('networkidle');
    await selectHeroVariant(page);
    await expect(page.locator('[data-testid="add-to-cart-button"]')).toBeEnabled();
    await page.click('[data-testid="add-to-cart-button"]');
    await expect(page.locator('[data-testid="cart-drawer"]')).toBeVisible();
    await page.click('[data-testid="cart-drawer-close"]');

    // Verify guest cookie exists
    const guestCookiesBefore = await context.cookies();
    const guestTokenBefore = guestCookiesBefore.find((c) => c.name === 'km_guest');
    expect(guestTokenBefore).toBeDefined();

    // 2. Register/login throwaway user
    const throwaway = await createThrowawayUser(request);
    await loginViaUI(page, throwaway.email, throwaway.password, 'en');

    // 3. User is logged in; cart should contain the merged item
    await page.goto('/en/cart');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="cart-item"]')).toHaveCount(1);
    await expect(page.locator('[data-testid="cart-item-name"]')).toHaveText(new RegExp(HERO.name, 'i'));

    // 4. Verify km_guest cookie is cleared (Max-Age=0 / deleted)
    const cookiesAfter = await context.cookies();
    const guestTokenAfter = cookiesAfter.find((c) => c.name === 'km_guest' && c.value !== '');
    expect(guestTokenAfter).toBeUndefined();
  });

  test('5 & 6: Checkout stepper (Address, Shipping, Payment, Review), idempotent order placement, COD success, and order cancellation', async ({ page, request }) => {
    // 1. Create a throwaway user and login
    const throwaway = await createThrowawayUser(request);
    await loginViaUI(page, throwaway.email, throwaway.password, 'en');

    // Add the hero product to the cart
    await page.goto(`/en/products/${HERO.slug}`);
    await page.waitForLoadState('networkidle');
    await selectHeroVariant(page);
    await expect(page.locator('[data-testid="add-to-cart-button"]')).toBeEnabled();
    await page.click('[data-testid="add-to-cart-button"]');
    await expect(page.locator('[data-testid="cart-drawer"]')).toBeVisible();
    await page.click('[data-testid="cart-drawer-close"]');

    // 2. Proceed to checkout
    await page.goto('/en/checkout');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('h1')).toHaveText(/Checkout/i);
    await expect(page.locator('[data-testid="step-address"]')).toBeVisible();

    // Step 1: Address
    // Add Siem Reap Address
    await page.click('[data-testid="add-new-address-btn"]');
    const dialog = page.locator('[data-testid="add-address-dialog"]');
    await expect(dialog).toBeVisible();

    await page.fill('[data-testid="address-recipient-name-input"]', 'Dara Angkor');
    await page.fill('[data-testid="address-phone-input"]', '+85512999888');
    await page.fill('[data-testid="address-line1-input"]', 'Street 6, Pub Street Area');
    await page.fill('[data-testid="address-district-input"]', 'Siem Reap');
    await page.selectOption('[data-testid="address-province-select"]', 'Siem Reap');
    await page.fill('[data-testid="address-postal-input"]', '17000');
    await page.fill('[data-testid="address-label-input"]', 'Siem Reap Home');

    // Screenshot: checkout-address.png
    await page.screenshot({ path: 'docs/screenshots/checkout-address.png' });

    const saveBtn = page.locator('[data-testid="save-address-btn"]');
    await saveBtn.scrollIntoViewIfNeeded();
    await saveBtn.click();
    await expect(dialog).not.toBeVisible({ timeout: 10000 });

    // Continue to Shipping
    await page.click('[data-testid="next-to-shipping-btn"]');
    await expect(page.locator('[data-testid="step-shipping"]')).toBeVisible();

    // Verify state survives page refresh!
    await page.reload();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="step-shipping"]')).toBeVisible();

    // Step 2: Shipping
    // Verify SAME_DAY delivery is DISABLED with clear reason for Siem Reap address
    const samedayReason = page.locator('[data-testid="sameday-disabled-reason"]');
    await expect(samedayReason).toBeVisible();
    await expect(samedayReason).toContainText(/Same-day delivery is only available in Phnom Penh/i);

    // Screenshot: checkout-shipping-sameday-disabled.png
    await page.screenshot({ path: 'docs/screenshots/checkout-shipping-sameday-disabled.png' });

    // Select STANDARD shipping
    await page.click('[data-testid="shipping-method-STANDARD"]');
    await page.click('[data-testid="next-to-payment-btn"]');

    // Step 3: Payment
    await expect(page.locator('[data-testid="step-payment"]')).toBeVisible();
    await expect(page.locator('[data-testid="payment-method-KHQR"]')).toContainText(/Recommended/i);

    // Select COD
    await page.click('[data-testid="payment-method-COD"]');
    await page.click('[data-testid="next-to-review-btn"]');

    // Step 4: Review
    await expect(page.locator('[data-testid="step-review"]')).toBeVisible();

    // Check preview numbers
    const totalUsdText = await page.locator('[data-testid="review-total-usd"]').innerText();
    const totalKhrText = await page.locator('[data-testid="review-total-khr"]').innerText();
    console.log('CHECKOUT REVIEW TOTALS:', { totalUsdText, totalKhrText });
    expect(totalUsdText).toMatch(/\$\d+/);
    expect(totalKhrText).toContain('៛');

    // Screenshot: checkout-review.png
    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/checkout-review.png', fullPage: true });

    // Query orders count before placing
    const ordersBefore = await request.get(`${API}/orders`, {
      headers: { Authorization: `Bearer ${throwaway.token}` },
    });
    const ordersBeforeJson = await ordersBefore.json();
    const countBefore = ordersBeforeJson.data?.length || 0;

    // Double-click "Place order" to verify idempotency
    const placeOrderBtn = page.locator('[data-testid="place-order-btn"]');
    await placeOrderBtn.dblclick();

    // Navigates to COD success page: /en/orders/{orderNumber}/success
    await page.waitForURL('**/orders/*/success');
    await expect(page.locator('h1')).toHaveText(/Order Placed Successfully/i);

    const orderNumber = await page.locator('[data-testid="order-success-number"]').innerText();
    console.log('PLACED ORDER NUMBER:', orderNumber);
    expect(orderNumber).toBeTruthy();

    // Verify "View order" button and "Continue shopping" button are visible with valid translated text
    const viewOrderBtn = page.locator('[data-testid="view-order-btn"]');
    await expect(viewOrderBtn).toBeVisible();
    await expect(viewOrderBtn).toHaveText(/View Order/i);

    const continueShoppingBtn = page.locator('[data-testid="continue-shopping-btn"]');
    await expect(continueShoppingBtn).toBeVisible();
    await expect(continueShoppingBtn).toHaveText(/Continue Shopping/i);

    // Screenshot: order-success-cod.png
    await page.screenshot({ path: 'docs/screenshots/order-success-cod.png' });

    // Verify GET /api/orders total after: EXACTLY ONE new order created!
    const ordersAfter = await request.get(`${API}/orders`, {
      headers: { Authorization: `Bearer ${throwaway.token}` },
    });
    const ordersAfterJson = await ordersAfter.json();
    const countAfter = ordersAfterJson.data?.length || 0;
    console.log('ORDERS COUNT BEFORE VS AFTER:', { countBefore, countAfter });
    expect(countAfter).toBe(countBefore + 1);

    // CRITICAL TEST DATA RULE: Cancel the created order so stock reservations are released!
    const cancelRes = await request.post(`${API}/orders/${orderNumber}/cancel`, {
      headers: {
        Authorization: `Bearer ${throwaway.token}`,
        'Content-Type': 'application/json',
      },
      data: { reason: 'Cancelled by Playwright E2E test' },
    });
    expect(cancelRes.ok()).toBeTruthy();
    console.log(`ORDER ${orderNumber} CANCELLED SUCCESSFULLY to release stock.`);
  });

  test('7: Mobile (390x844) full checkout layout with sticky summary and CTA', async ({ page, request }) => {
    await page.setViewportSize({ width: 390, height: 844 });

    const throwaway = await createThrowawayUser(request);
    await loginViaUI(page, throwaway.email, throwaway.password, 'en');

    // Add item to cart
    await page.goto(`/en/products/${HERO.slug}`);
    await page.waitForLoadState('networkidle');
    await selectHeroVariant(page);
    await expect(page.locator('[data-testid="add-to-cart-button"]')).toBeEnabled();
    await page.click('[data-testid="add-to-cart-button"]');
    await expect(page.locator('[data-testid="cart-drawer"]')).toBeVisible();
    await page.click('[data-testid="cart-drawer-close"]');

    // Navigate to checkout
    await page.goto('/en/checkout');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="checkout-page"]')).toBeVisible();

    // Verify mobile sticky bar is visible
    const stickyBar = page.locator('[data-testid="checkout-mobile-sticky-bar"]');
    await expect(stickyBar).toBeVisible();

    // Screenshot: checkout-mobile.png
    await page.screenshot({ path: 'docs/screenshots/checkout-mobile.png', fullPage: true });
  });

  test('8: Zero console errors on /cart and /checkout', async ({ page, request }) => {
    const throwaway = await createThrowawayUser(request);
    await loginViaUI(page, throwaway.email, throwaway.password, 'en');

    // Add item to cart for clean preview
    await page.goto(`/en/products/${HERO.slug}`);
    await page.waitForLoadState('networkidle');
    await selectHeroVariant(page);
    await expect(page.locator('[data-testid="add-to-cart-button"]')).toBeEnabled();
    await page.click('[data-testid="add-to-cart-button"]');
    await expect(page.locator('[data-testid="cart-drawer"]')).toBeVisible();
    await page.click('[data-testid="cart-drawer-close"]');

    const routes = ['/en/cart', '/en/checkout'];

    for (const route of routes) {
      const consoleErrors: string[] = [];
      const pageErrors: string[] = [];

      const consoleListener = (msg: any) => {
        const isNetwork401 = msg.text().includes('401') && msg.text().includes('Failed to load resource');
        if (msg.type() === 'error' && !isNetwork401) {
          consoleErrors.push(msg.text());
        }
      };
      const pageErrorListener = (err: Error) => {
        pageErrors.push(err.message);
      };

      page.on('console', consoleListener);
      page.on('pageerror', pageErrorListener);

      try {
        await page.goto(route);
        await page.waitForLoadState('networkidle');
        await page.waitForTimeout(500);

        if (consoleErrors.length > 0 || pageErrors.length > 0) {
          console.error(`Errors captured on route ${route}:`, { consoleErrors, pageErrors });
        }

        expect(pageErrors, `Page errors on ${route}`).toEqual([]);
        expect(consoleErrors, `Console errors on ${route}`).toEqual([]);
      } finally {
        page.off('console', consoleListener);
        page.off('pageerror', pageErrorListener);
      }
    }
  });

  test('9: Untranslated keys check: fails if any text matching /^[a-z]+\\.[a-zA-Z.]+$/ is visible on /cart, /checkout, and /success', async ({ page, request }) => {
    async function assertNoUntranslatedKeys(path: string) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');

      const untranslatedMatches = await page.evaluate(() => {
        const regex = /^[a-z]+\.[a-zA-Z.]+$/;
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
        const matches: string[] = [];
        let node: Node | null;
        while ((node = walker.nextNode())) {
          const el = node.parentElement;
          if (el && el.offsetParent !== null) {
            const text = node.textContent?.trim() || '';
            if (text && regex.test(text)) {
              matches.push(text);
            }
          }
        }
        return matches;
      });

      expect(untranslatedMatches, `Untranslated keys found on ${path}`).toEqual([]);
    }

    // Check /cart in EN and KM
    await assertNoUntranslatedKeys('/en/cart');
    await assertNoUntranslatedKeys('/km/cart');

    // Create a throwaway user and login
    const throwaway = await createThrowawayUser(request);
    await loginViaUI(page, throwaway.email, throwaway.password, 'en');

    // Add item to cart for checkout
    await page.goto(`/en/products/${HERO.slug}`);
    await page.waitForLoadState('networkidle');
    await selectHeroVariant(page);
    await page.click('[data-testid="add-to-cart-button"]');
    await page.click('[data-testid="cart-drawer-close"]');

    // Check /checkout in EN and KM
    await assertNoUntranslatedKeys('/en/checkout');
    await assertNoUntranslatedKeys('/km/checkout');

    // Create an address and place a COD order to test /success
    await page.goto('/en/checkout');
    await page.click('[data-testid="add-new-address-btn"]');
    await page.fill('[data-testid="address-recipient-name-input"]', 'Bopha Sok');
    await page.fill('[data-testid="address-phone-input"]', '+85512345678');
    await page.fill('[data-testid="address-line1-input"]', 'Street 2004');
    await page.fill('[data-testid="address-district-input"]', 'Sen Sok');
    await page.selectOption('[data-testid="address-province-select"]', 'Phnom Penh');
    await page.click('[data-testid="save-address-btn"]');
    await page.waitForSelector('[data-testid="add-address-dialog"]', { state: 'hidden' });

    await page.click('[data-testid="next-to-shipping-btn"]');
    await page.click('[data-testid="shipping-method-STANDARD"]');
    await page.click('[data-testid="next-to-payment-btn"]');
    await page.click('[data-testid="payment-method-COD"]');
    await page.click('[data-testid="next-to-review-btn"]');
    await page.click('[data-testid="place-order-btn"]');

    await page.waitForURL('**/orders/*/success');
    const successEnUrl = page.url();

    // Check success page in EN
    await assertNoUntranslatedKeys(successEnUrl);

    // Check success page in KM
    const successKmUrl = successEnUrl.replace('/en/', '/km/');
    await assertNoUntranslatedKeys(successKmUrl);

    // Cancel order to release stock
    const orderNumber = await page.locator('[data-testid="order-success-number"]').innerText();
    await request.post(`${API}/orders/${orderNumber}/cancel`, {
      headers: {
        Authorization: `Bearer ${throwaway.token}`,
        'Content-Type': 'application/json',
      },
      data: { reason: 'Clean up from untranslated keys test' },
    });
  });

});

test.describe('Phase 8 — Session restore race', () => {
  test('slow /auth/refresh on page load keeps the user signed in and the checkout address step reachable', async ({ page, request }) => {
    const throwaway = await createThrowawayUser(request);
    await request.post(`${API}/cart/items`, {
      headers: { Authorization: `Bearer ${throwaway.token}` },
      data: { productId: HERO.id, variantId: HERO.variants.redStandard, quantity: 1 },
    });
    await loginViaUI(page, throwaway.email, throwaway.password, 'en');

    // Session restore (silent refresh) takes longer than the API client's wait for AuthProvider
    await page.route('**/api/auth/refresh', async (route) => {
      await new Promise((r) => setTimeout(r, 2500));
      await route.continue();
    });
    await page.goto('/en/checkout');

    await expect(page.locator('[data-testid="add-new-address-btn"]')).toBeVisible();
    await expect(page).toHaveURL(/\/en\/checkout$/);
  });
});
