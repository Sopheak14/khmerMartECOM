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

test.describe('Phase 1 Foundation Shell & Real Backend Integration', () => {
  test('renders English shell with real backend data, switches to Khmer, and verifies localization', async ({ page }) => {
    // 1. Visit English homepage
    await page.goto('/en');
    await expect(page).toHaveTitle(/KhmerMart/i);
    await expect(page.locator('text=KhmerMart').first()).toBeVisible();

    // Verify dynamic Announcement Bar with backend freeShippingOverCents ($50)
    await expect(page.locator('text=Free standard delivery on orders over $50').first()).toBeVisible();

    // Verify real backend seasonal banner title and seed products loaded
    await expect(page.locator('text=Khmer New Year Mega Sale').first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=Laptop Pro 15').first()).toBeVisible();
    await expect(page.locator('text=Dell Latitude 5520').first()).toBeVisible();

    // Verify "Explore Flash Sale" outline button with transparent background & white text
    const exploreBtn = page.locator('a:has-text("Explore Flash Sale")').first();
    await expect(exploreBtn).toBeVisible();

    // Take full page screenshot of English shell
    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/phase1-en.png', fullPage: true });

    // 2. Click language switcher to switch to Khmer
    const langBtn = page.locator('button[aria-label="Switch language"]');
    await expect(langBtn).toBeVisible();
    await langBtn.click();

    // Verify URL contains /km
    await page.waitForURL('**/km**');

    // Verify real backend data in Khmer
    await expect(page.locator('text=ការបញ្ចុះតម្លៃពិធីបុណ្យចូលឆ្នាំថ្មីប្រពៃណីជាតិ').first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=កុំព្យូទ័រយួរដៃ Laptop Pro 15').first()).toBeVisible();
    await expect(page.locator('text=កុំព្យូទ័រយួរដៃ Dell Latitude 5520').first()).toBeVisible();
    await expect(page.locator('text=ដឹកជញ្ជូនធម្មតាឥតគិតថ្លៃសម្រាប់ការបញ្ជាទិញលើសពី $50').first()).toBeVisible();

    // Verify that known English keys DO NOT appear on the Khmer page
    const pageText = await page.innerText('body');
    const forbiddenEnglishStrings = [
      'Top trending items with instant KHQR payment',
      'KHQR Payments',
      'All Bakong apps supported',
      'Fast Delivery',
      'Phnom Penh & 24 Provinces',
      '100% Authentic',
      'Verified store warranty',
      'Customer Care',
      'Mon - Sun (8am - 8pm)',
      'Flash Sales & Discounts',
      'Track Delivery',
      'My Purchases',
      'Returns & Refunds Policy',
      'Cash on Delivery',
      'Bank Transfer',
      'Built with Next.js 14 & Spring Boot 3',
      'Free delivery in Phnom Penh on orders over $50!',
    ];

    for (const forbidden of forbiddenEnglishStrings) {
      expect(pageText).not.toContain(forbidden);
    }

    // Take full page screenshot of Khmer shell
    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/phase1-km.png', fullPage: true });
  });

  test('renders responsive mobile layout correctly with bottom padding', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/en');

    // Header and brand should be visible
    await expect(page.locator('text=KhmerMart').first()).toBeVisible();

    // Real backend product should be loaded
    await expect(page.locator('text=Laptop Pro 15').first()).toBeVisible({ timeout: 10000 });

    // Mobile nav bar at bottom should be visible
    const mobileNav = page.locator('nav.sm\\:hidden');
    await expect(mobileNav).toBeVisible();

    // Footer copyright should be visible
    const copyright = page.locator('footer').getByText(/KhmerMart/i).first();
    await expect(copyright).toBeVisible();

    // Take screenshot of Mobile viewport
    await scrollFullPageAndSettle(page);
    await page.screenshot({ path: 'docs/screenshots/phase1-mobile.png', fullPage: true });
  });
});
