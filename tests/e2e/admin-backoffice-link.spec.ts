import { test, expect } from '@playwright/test';
import { loginViaUI } from './helpers/auth';

test.describe('Admin Backoffice link', () => {
  for (const locale of ['en', 'km'] as const) {
    test(`user menu -> Admin Backoffice opens the dashboard (${locale})`, async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', locale);
      await page.locator('[data-testid="account-menu-button"]').click();
      await page.locator(`a[href="/${locale}/admin"]`).click();
      await expect(page).toHaveURL(new RegExp(`/${locale}/admin$`));
      await expect(page.locator('[data-testid="kpi-card-revenue"]')).toBeVisible({ timeout: 15000 });
      await expect(page.locator('[data-testid="kpi-orders-count"]')).toBeVisible();
    });
  }

  test('/en/admin/dashboard redirects to /en/admin', async ({ page }) => {
    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/dashboard');
    await expect(page).toHaveURL(/\/en\/admin$/);
    await expect(page.locator('[data-testid="kpi-card-revenue"]')).toBeVisible({ timeout: 15000 });
  });

  test('unknown /admin route shows the 404 page, not a blank page', async ({ page }) => {
    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/does-not-exist');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(/Page Not Found/i);
    await expect(page.locator('body')).not.toBeEmpty();
  });
});
