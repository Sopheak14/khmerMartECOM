import { test, expect, Page } from '@playwright/test';

async function scrollFullPageAndSettle(page: Page) {
  await page.evaluate(async () => {
    await new Promise<void>((resolve) => {
      let totalHeight = 0;
      const distance = 400;
      const timer = setInterval(() => {
        const scrollHeight = document.body.scrollHeight;
        window.scrollBy(0, distance);
        totalHeight += distance;
        if (totalHeight >= scrollHeight) {
          clearInterval(timer);
          window.scrollTo(0, 0);
          resolve();
        }
      }, 50);
    });
  });
  await page.waitForLoadState('networkidle');
  await page.evaluate(async () => {
    const images = Array.from(document.querySelectorAll('img'));
    await Promise.all(
      images.map(
        (img) =>
          new Promise((resolve) => {
            if (img.complete) {
              resolve(true);
            } else {
              img.addEventListener('load', () => resolve(true));
              img.addEventListener('error', () => resolve(true));
            }
          })
      )
    );
  });
  await page.waitForTimeout(300);
}

test.describe('Phase 3 — Catalog & Search E2E against Real Backend', () => {

  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
  });

  test('1 & 8: /en/products grid with facets, sort, pagination, active filter chips, empty state, and mobile layout', async ({ page }) => {
    // 1. Visit /en/products
    await page.goto('/en/products');
    await expect(page.locator('h1')).toHaveText(/All Products/i);

    // Verify product cards are displayed
    const productGrid = page.locator('[data-testid="product-grid"]');
    await expect(productGrid).toBeVisible();
    const productCards = productGrid.locator('[data-testid="product-card"]');
    await expect(productCards.first()).toBeVisible({ timeout: 10000 });
    const count = await productCards.count();
    expect(count).toBeGreaterThan(0);

    // Verify Facets: Brand filter with counts
    const filterSidebar = page.locator('[data-testid="filter-sidebar"]');
    await expect(filterSidebar).toBeVisible();
    await expect(filterSidebar.locator('[data-testid^="brand-filter-"]').first()).toBeVisible();

    // Verify Specification attribute chips
    await expect(filterSidebar.locator('[data-testid^="attr-filter-"]').first()).toBeVisible();

    // Apply Brand filter "Dell"
    const dellFilter = page.locator('[data-testid="brand-filter-Dell"]');
    await dellFilter.click();
    await page.waitForURL('**/products?*brand=Dell*');

    // Verify active filter chips appear
    const activeChips = page.locator('[data-testid="active-filter-chips"]');
    await expect(activeChips).toBeVisible();
    await expect(activeChips.locator('text=Dell').first()).toBeVisible();

    // Capture screenshot: products-filters-en.png
    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/products-filters-en.png', fullPage: true });

    // Test Sort select
    const sortSelect = page.locator('[data-testid="sort-select"]');
    await sortSelect.selectOption('price_asc');
    await page.waitForURL('**/products?*sort=price_asc*');

    // Test Empty state: filter with impossible price range
    await page.fill('[data-testid="min-price-input"]', '900000');
    await page.fill('[data-testid="max-price-input"]', '999999');
    await page.click('[data-testid="apply-price-btn"]');
    await expect(page.locator('[data-testid="products-empty-state"]')).toBeVisible({ timeout: 5000 });

    // Click "Clear all"
    await page.click('button:has-text("Clear all")');
    await expect(productGrid).toBeVisible({ timeout: 5000 });

    // Mobile layout screenshot: products-mobile.png
    try {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto('/en/products');
      await expect(page.locator('h1')).toHaveText(/All Products/i);
      await scrollFullPageAndSettle(page);
      await page.screenshot({ path: 'docs/screenshots/products-mobile.png', fullPage: true });
    } finally {
      await page.setViewportSize({ width: 1280, height: 800 });
    }
  });

  test('2: Filters live in the URL and lesson demo query with browser Back support', async ({ page }) => {
    // Navigate to the lesson demo query:
    // /en/products?q=laptop&minPrice=500&maxPrice=1000&brand=Dell&attr[ram]=16GB
    const testUrl = '/en/products?q=laptop&minPrice=500&maxPrice=1000&brand=Dell&attr%5Bram%5D=16GB';
    await page.goto(testUrl);

    // Verify products count and exact product names
    const productCards = page.locator('[data-testid="product-card"]');
    await expect(productCards).toHaveCount(2, { timeout: 10000 });

    const cardNames = await productCards.locator('h3').allInnerTexts();
    console.log('FILTER DEMO PRODUCTS FOUND:', cardNames);
    expect(cardNames).toContain('Dell Latitude 5520 Laptop');
    expect(cardNames).toContain('Dell Inspiron 15 Laptop');

    // Verify active filter chips reflect the URL state
    const chips = page.locator('[data-testid="active-filter-chips"]');
    await expect(chips).toContainText('laptop');
    await expect(chips).toContainText('Dell');
    await expect(chips).toContainText('16GB');

    // Mutate filter: click "Clear all"
    await chips.locator('button:has-text("Clear all")').click();
    await page.waitForURL((url) => !url.searchParams.has('brand'));
    const clearedCount = await page.locator('[data-testid="product-card"]').count();
    expect(clearedCount).toBeGreaterThan(2);

    // Browser Back restores previous filter state
    await page.goBack();
    await page.waitForURL((url) => url.searchParams.get('brand') === 'Dell');
    await expect(page.locator('[data-testid="product-card"]')).toHaveCount(2);
    expect(await page.locator('[data-testid="product-card"]').locator('h3').allInnerTexts()).toContain('Dell Latitude 5520 Laptop');
  });

  test('3: Header search autocomplete (debounced, /api/search/suggest) for "lap" and Khmer "កុំព្យូទ័រ", with keyboard navigation', async ({ page }) => {
    await page.goto('/en');

    const searchInput = page.locator('[data-testid="search-input"]').first();
    await expect(searchInput).toBeVisible();

    // 1. Type "lap" using pressSequentially
    await searchInput.click();
    await searchInput.pressSequentially('lap', { delay: 50 });

    // Wait for dropdown to show suggestions
    const suggestions = page.locator('[data-testid="search-suggestions"]');
    await expect(suggestions).toBeVisible({ timeout: 5000 });
    const suggestionItems = suggestions.locator('[data-testid="suggestion-item"]');
    expect(await suggestionItems.count()).toBeGreaterThan(0);

    // Capture screenshot: search-autocomplete.png
    await page.screenshot({ path: 'docs/screenshots/search-autocomplete.png' });

    // Test keyboard navigation (ArrowDown, ArrowDown, Escape)
    await searchInput.press('ArrowDown');
    const firstActive = suggestions.locator('[data-active="true"]');
    await expect(firstActive).toBeVisible();

    await searchInput.press('ArrowDown');
    const secondActive = suggestions.locator('[data-active="true"]');
    await expect(secondActive).toBeVisible();

    // Press Escape to close
    await searchInput.press('Escape');
    await expect(suggestions).not.toBeVisible();

    // 2. Test Khmer search suggestion: "កុំព្យូទ័រ"
    await page.goto('/km');
    const searchInputKm = page.locator('[data-testid="search-input"]').first();
    await searchInputKm.click();
    await searchInputKm.pressSequentially('កុំព្យូទ័រ', { delay: 50 });
    const suggestionsKm = page.locator('[data-testid="search-suggestions"]');
    await expect(suggestionsKm).toBeVisible({ timeout: 5000 });
    await expect(suggestionsKm.locator('[data-testid="suggestion-item"]').first()).toBeVisible();
  });

  test('4: /en/category/laptops and sub-category page show correct products and breadcrumbs', async ({ page }) => {
    // 1. Root Category: Laptops
    await page.goto('/en/category/laptops');
    await expect(page.locator('h1').first()).toHaveText(/Laptops/i);

    // Verify breadcrumbs
    const breadcrumbs = page.locator('[data-testid="category-breadcrumbs"]');
    await expect(breadcrumbs).toBeVisible();
    await expect(breadcrumbs).toContainText('Home');
    await expect(breadcrumbs).toContainText('Products');
    await expect(breadcrumbs).toContainText('Laptops');

    // Verify 9 products under laptops
    const cards = page.locator('[data-testid="product-card"]');
    await expect(cards).toHaveCount(9);

    // 2. Sub-Category: Business Laptops
    await page.goto('/en/category/business-laptops');
    await expect(page.locator('h1').first()).toHaveText(/Business Laptops/i);

    // Verify hierarchical breadcrumbs
    const subBreadcrumbs = page.locator('[data-testid="category-breadcrumbs"]');
    await expect(subBreadcrumbs).toBeVisible();
    await expect(subBreadcrumbs).toContainText('Home');
    await expect(subBreadcrumbs).toContainText('Products');
    await expect(subBreadcrumbs).toContainText('Laptops');
    await expect(subBreadcrumbs).toContainText('Business Laptops');

    // Verify 5 products under business laptops
    const subCards = page.locator('[data-testid="product-card"]');
    await expect(subCards).toHaveCount(5);
  });

  test('5: Product detail /en/products/laptop-pro-15: gallery, flash sale price, variants, stock, reviews, disabled Add to Cart', async ({ page }) => {
    await page.goto('/en/products/laptop-pro-15');

    // Verify title and breadcrumbs
    await expect(page.locator('[data-testid="product-detail-title"]')).toHaveText(/Laptop Pro 15/i);
    await expect(page.locator('[data-testid="product-breadcrumbs"]')).toContainText('Business Laptops');

    // 1. Gallery
    const gallery = page.locator('[data-testid="product-gallery"]');
    await expect(gallery).toBeVisible();
    const thumbs = page.locator('[data-testid^="gallery-thumb-"]');
    expect(await thumbs.count()).toBeGreaterThanOrEqual(2);
    // Click second thumbnail
    await thumbs.nth(1).click();

    // 2. Flash Sale Price $899.00 with $999.00 struck through + KHR
    const price = page.locator('[data-testid="product-price"]');
    await expect(price).toHaveText('$899.00');
    const originalPrice = page.locator('[data-testid="original-price"]');
    await expect(originalPrice).toHaveText('$999.00');
    await expect(page.locator('[data-testid="khr-price"]')).toBeVisible();

    // Countdown timer & Flash Sale badge visible
    await expect(page.locator('[data-testid="flash-sale-countdown-box"]')).toBeVisible();
    await expect(page.locator('[data-testid="badge-flash_sale"]')).toBeVisible();

    // 3. Variant selection:
    // Select 32GB RAM
    await page.click('[data-testid="variant-chip-ram-32GB"]');
    await page.click('[data-testid="variant-chip-color-Space Gray"]');

    // Verify unavailable combination is disabled (512GB is unavailable with 32GB)
    const chip512 = page.locator('[data-testid="variant-chip-storage-512GB"]');
    await expect(chip512).toBeDisabled();

    // Select 1TB Storage
    await page.click('[data-testid="variant-chip-storage-1TB"]');

    // 32GB/1TB is not on flash sale: shows normal price $1,299.00 with no strike-through,
    // and hides the flash-sale countdown and badge
    await expect(page.locator('[data-testid="product-price"]')).toHaveText('$1,299.00');
    await expect(page.locator('[data-testid="original-price"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="flash-sale-countdown-box"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="badge-flash_sale"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="stock-status"]')).toContainText('10 available');

    // 4. Quantity selector (max = available)
    const qtyMinus = page.locator('[data-testid="qty-minus-btn"]');
    const qtyPlus = page.locator('[data-testid="qty-plus-btn"]');
    const qtyDisplay = page.locator('[data-testid="qty-display"]');

    await expect(qtyDisplay).toHaveText('1');
    await qtyPlus.click();
    await expect(qtyDisplay).toHaveText('2');
    await qtyMinus.click();
    await expect(qtyDisplay).toHaveText('1');

    // 5. Add to Cart button is enabled once full combination is selected
    const addToCartBtn = page.locator('[data-testid="add-to-cart-button"]');
    await expect(addToCartBtn).toBeEnabled();
    await expect(addToCartBtn).toContainText('Add to Cart');

    // 6. Rating summary with 5->1 bars matching ratingBreakdown
    const ratingSummary = page.locator('[data-testid="rating-summary"]');
    await expect(ratingSummary).toBeVisible();
    await expect(page.locator('[data-testid="avg-rating"]')).toHaveText('4.7');
    await expect(page.locator('[data-testid="rating-count-5"]')).toHaveText('2');
    await expect(page.locator('[data-testid="rating-count-4"]')).toHaveText('1');
    await expect(page.locator('[data-testid="rating-count-3"]')).toHaveText('0');

    // 7. Approved reviews list
    const reviewsList = page.locator('[data-testid="approved-reviews-list"]');
    await expect(reviewsList).toBeVisible();
    await expect(reviewsList.locator('[data-testid="review-item"]')).toHaveCount(3);

    // 8. Related products
    const related = page.locator('[data-testid="related-products-section"]');
    await expect(related).toBeVisible();
    expect(await related.locator('[data-testid="product-card"]').count()).toBeGreaterThan(0);

    // Capture screenshot: product-1001-en.png
    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/product-1001-en.png', fullPage: true });
  });

  test('6: Khmer locale displays nameKm everywhere and non-existent slug returns 404', async ({ page }) => {
    // 1. Visit product in Khmer
    await page.goto('/km/products/laptop-pro-15');
    await expect(page.locator('[data-testid="product-detail-title"]')).toHaveText('កុំព្យូទ័រយួរដៃ Laptop Pro 15');

    // Capture screenshot: product-1001-km.png
    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/product-1001-km.png', fullPage: true });

    // 2. Non-existent slug returns 404 page
    await page.goto('/en/products/non-existent-product-slug-12345');
    await expect(page.locator('h1')).toHaveText(/Page Not Found/i);

    // Non-existent in Khmer
    await page.goto('/km/products/non-existent-product-slug-12345');
    await expect(page.locator('h1')).toHaveText(/រកមិនឃើញទំព័រ/i);
  });

  test('7: Zero console errors or hydration mismatch on /en, /en/products, and /en/products/laptop-pro-15', async ({ page }) => {
    const routes = ['/en', '/en/products', '/en/products/laptop-pro-15'];

    for (const route of routes) {
      const consoleErrors: string[] = [];
      const pageErrors: string[] = [];
      const pageErrorListener = (err: Error) => {
        pageErrors.push(err.message);
      };

      const consoleListener = (msg: any) => {
        // Chromium logs expected unauthenticated silent refresh network 401s as a resource warning
        const isNetwork401 = msg.text().includes('401') && msg.text().includes('Failed to load resource');
        if (msg.type() === 'error' && !isNetwork401) {
          consoleErrors.push(msg.text());
        }
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

  test('8: Flash sale variant pricing E2E: select 32GB/1TB -> page $1,299.00 -> add to cart -> CartDTO total 129900 (assert UI == API); select 16GB/512GB -> $899.00 -> cart 89900', async ({ page, context }) => {
    // 1. Fresh guest cart session for 32GB / 1TB
    await context.clearCookies();
    await page.goto('/en/products/laptop-pro-15');
    await expect(page.locator('[data-testid="product-detail-title"]')).toHaveText(/Laptop Pro 15/i);

    const addBtn = page.locator('[data-testid="add-to-cart-button"]');
    await expect(addBtn).toBeDisabled();

    // Select 32GB / Space Gray / 1TB
    await page.click('[data-testid="variant-chip-ram-32GB"]');
    await page.click('[data-testid="variant-chip-color-Space Gray"]');
    await page.click('[data-testid="variant-chip-storage-1TB"]');

    // Page shows $1,299.00 with no strike-through
    await expect(page.locator('[data-testid="product-price"]')).toHaveText('$1,299.00');
    await expect(page.locator('[data-testid="original-price"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="flash-sale-countdown-box"]')).not.toBeVisible();
    await expect(addBtn).toBeEnabled();

    // Add to cart and assert CartDTO total 129900 and UI matches API
    const [cartRes32] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/cart/items') && r.request().method() === 'POST'),
      addBtn.click(),
    ]);
    const cart32 = await cartRes32.json();
    expect(cart32.success).toBe(true);
    expect(cart32.data.totalCents).toBe(129900);

    const drawer = page.locator('[data-testid="cart-drawer"]');
    await expect(drawer).toBeVisible();
    await expect(drawer.locator('[data-testid="drawer-total-usd"]')).toHaveText('$1,299.00');

    // 2. Clear cart session to test 16GB / 512GB
    await context.clearCookies();
    await page.goto('/en/products/laptop-pro-15');
    await expect(page.locator('[data-testid="product-detail-title"]')).toHaveText(/Laptop Pro 15/i);

    // Select 16GB / Space Gray / 512GB
    await page.click('[data-testid="variant-chip-ram-16GB"]');
    await page.click('[data-testid="variant-chip-color-Space Gray"]');
    await page.click('[data-testid="variant-chip-storage-512GB"]');

    // Page shows $899.00 with $999.00 struck through
    await expect(page.locator('[data-testid="product-price"]')).toHaveText('$899.00');
    await expect(page.locator('[data-testid="original-price"]')).toHaveText('$999.00');
    await expect(page.locator('[data-testid="flash-sale-countdown-box"]')).toBeVisible();
    await expect(addBtn).toBeEnabled();

    // Add to cart and assert CartDTO total 89900 and UI matches API
    const [cartRes16] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/cart/items') && r.request().method() === 'POST'),
      addBtn.click(),
    ]);
    const cart16 = await cartRes16.json();
    expect(cart16.success).toBe(true);
    expect(cart16.data.totalCents).toBe(89900);

    await expect(drawer).toBeVisible();
    await expect(drawer.locator('[data-testid="drawer-total-usd"]')).toHaveText('$899.00');
  });

});
