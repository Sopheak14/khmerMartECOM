import { test, expect, APIRequestContext } from '@playwright/test';
import { loginViaUI } from './helpers/auth';
import { adminToken, createThrowawayUser, createAddress, placeKhqrOrder } from './helpers/orders';
import { API, CATEGORY } from './fixtures/catalog';

// Throwaway products only: created per test, and either hard-deleted by the UI or left inactive (soft-deleted).
async function createProduct(request: APIRequestContext, label: string) {
  const token = await adminToken(request);
  const sku = `DEL-${label}-${Date.now()}`;
  const res = await request.post(`${API}/admin/products`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: `Delete ${sku}`, sku, categoryId: CATEGORY.silk.id, priceCents: 500, active: true, initialStock: 5 },
  });
  expect(res.ok()).toBe(true);
  const d = (await res.json()).data;
  created.push(d.id as number);
  return { id: d.id as number, slug: d.slug as string, sku };
}

// Safety net: if a test fails midway the throwaway product must not stay active in the public catalog.
// Deleting an already-deleted product just fails quietly; a product with orders is soft-deleted (inactive).
const created: number[] = [];
test.afterEach(async ({ request }) => {
  const token = await adminToken(request);
  for (const id of created.splice(0)) {
    await request.delete(`${API}/admin/products/${id}`, { headers: { Authorization: `Bearer ${token}` } });
  }
});

for (const locale of ['en', 'km'] as const) {
  test.describe(`Admin product delete (${locale})`, () => {
    test('throwaway product disappears without reload and storefront returns 404', async ({ page, request }) => {
      const p = await createProduct(request, 'hard');
      expect((await request.get(`${API}/products/${p.id}`)).status()).toBe(200);

      const deleteReqs: string[] = [];
      page.on('request', (r) => { if (r.method() === 'DELETE') deleteReqs.push(r.url()); });

      await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', locale);
      await page.goto(`/${locale}/admin/products?q=${p.sku}`);
      await page.getByTestId(`delete-product-btn-${p.id}`).click();
      await page.getByTestId('confirm-delete-product-btn').click();

      await expect(page.getByTestId(`delete-product-btn-${p.id}`)).toHaveCount(0);
      await expect(page.getByTestId('confirm-delete-product-btn')).toHaveCount(0);
      expect(deleteReqs).toEqual([`${API}/admin/products/${p.id}`]);
      expect((await request.get(`${API}/products/${p.id}`)).status()).toBe(404);
      expect((await page.request.get(`/${locale}/products/${p.slug}`)).status()).toBe(404);
    });

    test('product with orders is soft-deleted: shown Inactive in admin, 404 in storefront', async ({ page, request }) => {
      const p = await createProduct(request, 'soft');
      const user = await createThrowawayUser(request);
      await placeKhqrOrder(request, user, await createAddress(request, user), { productId: p.id });

      await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', locale);
      await page.goto(`/${locale}/admin/products?q=${p.sku}`);
      await expect(page.getByTestId(`product-status-${p.id}`)).toHaveText(locale === 'en' ? 'Active' : 'កំពុងលក់');
      await page.getByTestId(`delete-product-btn-${p.id}`).click();
      await page.getByTestId('confirm-delete-product-btn').click();

      await expect(page.getByTestId('confirm-delete-product-btn')).toHaveCount(0);
      await expect(page.getByTestId(`product-status-${p.id}`)).toHaveText(locale === 'en' ? 'Inactive' : 'ផ្អាក');
      await expect(page.getByTestId(`delete-product-btn-${p.id}`)).toBeDisabled();
      expect((await request.get(`${API}/products/${p.id}`)).status()).toBe(404);
      expect((await page.request.get(`/${locale}/products/${p.slug}`)).status()).toBe(404);
      await page.goto(`/${locale}/search?q=${p.sku}`);
      await expect(page.getByText(p.sku)).toHaveCount(0);
    });
  });
}
