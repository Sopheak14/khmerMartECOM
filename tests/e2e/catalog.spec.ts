import { test, expect, Page } from '@playwright/test';
import { API, CATEGORY, HERO, SEARCH, usd } from './fixtures/catalog';
import { selectHeroVariant } from './helpers/shop';

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

    // Apply a Brand filter taken from the live facets (first brand listed)
    const brandFilter = filterSidebar.locator('[data-testid^="brand-filter-"]').first();
    const brand = (await brandFilter.getAttribute('data-testid'))!.replace('brand-filter-', '');
    await brandFilter.click();
    await page.waitForURL(/\/products\?.*brand=/);

    // Verify active filter chips appear
    const activeChips = page.locator('[data-testid="active-filter-chips"]');
    await expect(activeChips).toBeVisible();
    await expect(activeChips.locator(`text=${brand}`).first()).toBeVisible();

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

  test('2: Filter demo: search "kampot" then a price range, filters live in the URL, browser Back restores state', async ({ page, request }) => {
    // Expected result comes from the API with the same filters, not from a hard-coded list
    const apiRes = await request.get(`${API}/products`, {
      params: { q: SEARCH.query, minPrice: SEARCH.minDollars, maxPrice: SEARCH.maxDollars, size: 50 },
    });
    const expectedNames: string[] = (await apiRes.json()).data.map((p: { name: string }) => p.name);
    const allRes = await request.get(`${API}/products`, { params: { q: SEARCH.query, size: 50 } });
    const allNames: string[] = (await allRes.json()).data.map((p: { name: string }) => p.name);
    expect(expectedNames.length).toBeGreaterThan(0);
    expect(allNames.length).toBeGreaterThan(expectedNames.length);

    // Step 1: search only
    await page.goto(`/en/products?q=${SEARCH.query}`);
    const productCards = page.locator('[data-testid="product-card"]');
    await expect(productCards).toHaveCount(allNames.length, { timeout: 10000 });

    // Step 2: add the price range through the URL (the same state the sidebar writes)
    await page.goto(`/en/products?q=${SEARCH.query}&minPrice=${SEARCH.minDollars}&maxPrice=${SEARCH.maxDollars}`);
    await expect(productCards).toHaveCount(expectedNames.length, { timeout: 10000 });
    const cardNames = await productCards.locator('h3').allInnerTexts();
    console.log('FILTER DEMO PRODUCTS FOUND:', cardNames);
    expect([...cardNames].sort()).toEqual([...expectedNames].sort());

    // Verify active filter chips reflect the URL state
    const chips = page.locator('[data-testid="active-filter-chips"]');
    await expect(chips).toContainText(SEARCH.query);

    // Mutate filter: click "Clear all"
    await chips.locator('button:has-text("Clear all")').click();
    await page.waitForURL((url) => !url.searchParams.has('minPrice'));
    const clearedCount = await page.locator('[data-testid="product-card"]').count();
    expect(clearedCount).toBeGreaterThan(expectedNames.length);

    // Browser Back restores previous filter state
    await page.goBack();
    await page.waitForURL((url) => url.searchParams.has('minPrice'));
    await expect(page.locator('[data-testid="product-card"]')).toHaveCount(expectedNames.length);
  });

  test('3: Header search autocomplete (debounced, /api/search/suggest) for "kam" and Khmer "សូត្រ", with keyboard navigation', async ({ page }) => {
    await page.goto('/en');

    const searchInput = page.locator('[data-testid="search-input"]').first();
    await expect(searchInput).toBeVisible();

    // 1. Type "kam" (Kampot / Kampong products) using pressSequentially
    await searchInput.click();
    await searchInput.pressSequentially('kam', { delay: 50 });

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

    // 2. Test Khmer search suggestion: "សូត្រ" (silk)
    await page.goto('/km');
    const searchInputKm = page.locator('[data-testid="search-input"]').first();
    await searchInputKm.click();
    await searchInputKm.pressSequentially('សូត្រ', { delay: 50 });
    const suggestionsKm = page.locator('[data-testid="search-suggestions"]');
    await expect(suggestionsKm).toBeVisible({ timeout: 5000 });
    await expect(suggestionsKm.locator('[data-testid="suggestion-item"]').first()).toBeVisible();
  });

  test('4: Category page /en/category/silk-textiles shows its products and breadcrumbs, in EN and KM', async ({ page, request }) => {
    const apiRes = await request.get(`${API}/products`, { params: { categoryId: CATEGORY.silk.id, size: 50 } });
    const expectedNames: string[] = (await apiRes.json()).data.map((p: { name: string }) => p.name);
    expect(expectedNames).toContain(HERO.name);

    await page.goto(`/en/category/${CATEGORY.silk.slug}`);
    await expect(page.locator('h1').first()).toHaveText(CATEGORY.silk.name);

    const breadcrumbs = page.locator('[data-testid="category-breadcrumbs"]');
    await expect(breadcrumbs).toBeVisible();
    await expect(breadcrumbs).toContainText('Home');
    await expect(breadcrumbs).toContainText('Products');
    await expect(breadcrumbs).toContainText(CATEGORY.silk.name);

    const cards = page.locator('[data-testid="product-card"]');
    await expect(cards).toHaveCount(expectedNames.length);
    expect([...(await cards.locator('h3').allInnerTexts())].sort()).toEqual([...expectedNames].sort());

    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/category-silk-en.png', fullPage: true });

    await page.goto(`/km/category/${CATEGORY.silk.slug}`);
    await expect(page.locator('h1').first()).toHaveText(CATEGORY.silk.nameKm);
    await expect(page.locator('[data-testid="category-breadcrumbs"]')).toContainText(CATEGORY.silk.nameKm);
  });

  test('5: Product detail: hero Premium $199.00 has no flash sale; Standard $129.00 with $149.00 struck through and a countdown', async ({ page }) => {
    await page.goto(`/en/products/${HERO.slug}`);

    // Verify title and breadcrumbs
    await expect(page.locator('[data-testid="product-detail-title"]')).toHaveText(HERO.name);
    await expect(page.locator('[data-testid="product-breadcrumbs"]')).toContainText(CATEGORY.silk.name);

    // 1. Gallery (seed has a single image per product: no thumbnails strip)
    await expect(page.locator('[data-testid="product-gallery"]')).toBeVisible();

    // 2. Variants: Add to Cart disabled until color + grade chosen
    const addToCartBtn = page.locator('[data-testid="add-to-cart-button"]');
    await expect(addToCartBtn).toBeDisabled();

    // 3. Premium: regular price, no flash sale
    await selectHeroVariant(page, 'Premium');
    await expect(page.locator('[data-testid="product-price"]')).toHaveText(usd(HERO.premiumCents));
    await expect(page.locator('[data-testid="original-price"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="flash-sale-countdown-box"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="badge-flash_sale"]')).not.toBeVisible();

    // 4. Standard: flash price with the regular price struck through, countdown and badge visible
    await selectHeroVariant(page, 'Standard');
    await expect(page.locator('[data-testid="product-price"]')).toHaveText(usd(HERO.flashCents));
    await expect(page.locator('[data-testid="original-price"]')).toHaveText(usd(HERO.standardCents));
    await expect(page.locator('[data-testid="khr-price"]')).toBeVisible();
    await expect(page.locator('[data-testid="flash-sale-countdown-box"]')).toBeVisible();
    await expect(page.locator('[data-testid="badge-flash_sale"]')).toBeVisible();
    await expect(page.locator('[data-testid="stock-status"]')).toBeVisible();

    // 5. Quantity selector (max = available)
    const qtyMinus = page.locator('[data-testid="qty-minus-btn"]');
    const qtyPlus = page.locator('[data-testid="qty-plus-btn"]');
    const qtyDisplay = page.locator('[data-testid="qty-display"]');

    await expect(qtyDisplay).toHaveText('1');
    await qtyPlus.click();
    await expect(qtyDisplay).toHaveText('2');
    await qtyMinus.click();
    await expect(qtyDisplay).toHaveText('1');

    await expect(addToCartBtn).toBeEnabled();
    await expect(addToCartBtn).toContainText('Add to Cart');

    // 6. Description: only the English paragraph on /en
    const description = page.locator('.prose');
    await expect(description).toContainText(HERO.descEn);
    await expect(description).not.toContainText(HERO.descKm);

    // 7. Rating summary (seed reviews 5, 5, 4)
    await expect(page.locator('[data-testid="rating-summary"]')).toBeVisible();
    await expect(page.locator('[data-testid="avg-rating"]')).toHaveText('4.7');
    await expect(page.locator('[data-testid="rating-count-5"]')).toHaveText('2');
    await expect(page.locator('[data-testid="rating-count-4"]')).toHaveText('1');
    await expect(page.locator('[data-testid="rating-count-3"]')).toHaveText('0');
    await expect(page.locator('[data-testid="approved-reviews-list"] [data-testid="review-item"]')).toHaveCount(3);

    // 8. Related products
    const related = page.locator('[data-testid="related-products-section"]');
    await expect(related).toBeVisible();
    expect(await related.locator('[data-testid="product-card"]').count()).toBeGreaterThan(0);

    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/product-1001-en.png', fullPage: true });
  });

  test('6: Khmer locale displays nameKm everywhere and non-existent slug returns 404', async ({ page }) => {
    // 1. Visit product in Khmer
    await page.goto(`/km/products/${HERO.slug}`);
    await expect(page.locator('[data-testid="product-detail-title"]')).toHaveText(HERO.nameKm);

    // Description shows only the Khmer paragraph
    const description = page.locator('.prose');
    await expect(description).toContainText(HERO.descKm);
    await expect(description).not.toContainText(HERO.descEn);

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

  test('7: Zero console errors or hydration mismatch on /en, /en/products, and the hero product page', async ({ page }) => {
    const routes = ['/en', '/en/products', `/en/products/${HERO.slug}`];

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

  test('8: Flash sale variant pricing E2E: Premium -> $199.00 -> cart 19900 (assert UI == API); Standard -> $129.00 -> cart 12900', async ({ page, context }) => {
    // 1. Fresh guest cart session for Premium
    await context.clearCookies();
    await page.goto(`/en/products/${HERO.slug}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-testid="product-detail-title"]')).toHaveText(HERO.name);

    const addBtn = page.locator('[data-testid="add-to-cart-button"]');
    await expect(addBtn).toBeDisabled();

    await selectHeroVariant(page, 'Premium');
    await expect(page.locator('[data-testid="product-price"]')).toHaveText(usd(HERO.premiumCents));
    await expect(page.locator('[data-testid="original-price"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="flash-sale-countdown-box"]')).not.toBeVisible();
    await expect(addBtn).toBeEnabled();

    const [cartResPremium] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/cart/items') && r.request().method() === 'POST'),
      addBtn.click(),
    ]);
    const cartPremium = await cartResPremium.json();
    expect(cartPremium.success).toBe(true);
    expect(cartPremium.data.totalCents).toBe(HERO.premiumCents);

    const drawer = page.locator('[data-testid="cart-drawer"]');
    await expect(drawer).toBeVisible();
    await expect(drawer.locator('[data-testid="drawer-total-usd"]')).toHaveText(usd(HERO.premiumCents));

    // 2. Clear cart session to test Standard
    await context.clearCookies();
    await page.goto(`/en/products/${HERO.slug}`);
    await expect(page.locator('[data-testid="product-detail-title"]')).toHaveText(HERO.name);

    await selectHeroVariant(page, 'Standard');
    await expect(page.locator('[data-testid="product-price"]')).toHaveText(usd(HERO.flashCents));
    await expect(page.locator('[data-testid="original-price"]')).toHaveText(usd(HERO.standardCents));
    await expect(page.locator('[data-testid="flash-sale-countdown-box"]')).toBeVisible();
    await expect(addBtn).toBeEnabled();

    const [cartResStandard] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/cart/items') && r.request().method() === 'POST'),
      addBtn.click(),
    ]);
    const cartStandard = await cartResStandard.json();
    expect(cartStandard.success).toBe(true);
    expect(cartStandard.data.totalCents).toBe(HERO.flashCents);

    await expect(drawer).toBeVisible();
    await expect(drawer.locator('[data-testid="drawer-total-usd"]')).toHaveText(usd(HERO.flashCents));
  });

});
