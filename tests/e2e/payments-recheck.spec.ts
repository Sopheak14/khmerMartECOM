import { test, expect } from '@playwright/test';
import { API } from './fixtures/catalog';
import { createThrowawayUser, loginViaUI } from './helpers/auth';
import { createAddress, placeKhqrOrder } from './helpers/orders';

test.describe('Admin payments: KHQR re-check button', () => {
  test('appears for an EXPIRED KHQR payment (and not for a PAID one)', async ({ page, request }) => {
    const user = await createThrowawayUser(request);
    const auth = { Authorization: `Bearer ${user.token}` };
    const addressId = await createAddress(request, user);

    // Order 1: regenerating the QR expires the first payment.
    const first = await placeKhqrOrder(request, user, addressId);
    const regen = await request.post(`${API}/payments/khqr/${first.orderNumber}/regenerate`, { headers: auth });
    expect(regen.ok()).toBe(true);
    const status = await request.get(`${API}/payments/${first.paymentId}/status`, { headers: auth });
    expect((await status.json()).data.status).toBe('EXPIRED');

    // Order 2: a PAID payment must not offer a re-check.
    const paid = await placeKhqrOrder(request, user, addressId, { pay: true });

    await loginViaUI(page, 'admin@khmermart.local', 'Admin@12345', 'en');
    await page.goto('/en/admin/payments?method=KHQR&status=EXPIRED');
    await expect(page.locator(`[data-testid="recheck-payment-btn-${first.paymentId}"]`)).toBeVisible();
    await expect(page.locator(`[data-testid="recheck-payment-btn-${first.paymentId}"]`)).toHaveText(/Re-check payment now/i);

    await page.goto('/en/admin/payments?method=KHQR&status=PAID');
    await expect(page.locator(`[data-testid="refund-payment-btn-${paid.paymentId}"]`)).toBeVisible();
    await expect(page.locator(`[data-testid="recheck-payment-btn-${paid.paymentId}"]`)).toHaveCount(0);
  });
});
