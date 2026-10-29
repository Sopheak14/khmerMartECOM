import { Page, expect } from '@playwright/test';
import { HERO } from '../fixtures/catalog';

type Grade = 'Standard' | 'Premium';

/** Pick a full hero variant combination (color x grade) on the product page. */
export async function selectHeroVariant(page: Page, grade: Grade = 'Standard', color: string = HERO.color) {
  await page.click(`[data-testid="variant-chip-color-${color}"]`);
  await page.click(`[data-testid="variant-chip-grade-${grade}"]`);
}

/** Open the hero product, choose a variant and add it to the cart (leaves the cart drawer closed). */
export async function addHeroToCart(page: Page, grade: Grade = 'Standard') {
  await page.goto(`/en/products/${HERO.slug}`);
  await page.waitForLoadState('networkidle');
  await selectHeroVariant(page, grade);
  const addBtn = page.locator('[data-testid="add-to-cart-button"]');
  await expect(addBtn).toBeEnabled();
  await addBtn.click();
  await expect(page.locator('[data-testid="cart-drawer"]')).toBeVisible();
  await page.click('[data-testid="cart-drawer-close"]');
}
