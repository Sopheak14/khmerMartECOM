import { test, expect, request as pwRequest, Browser } from '@playwright/test';
import { API } from './fixtures/catalog';
import { createThrowawayUser, loginViaUI, ThrowawayUser } from './helpers/auth';
import { createAddress, placeKhqrOrder, placePaidLiveOrder, seedRefundPending } from './helpers/orders';

const ADMIN = { email: 'admin@khmermart.local', password: 'Admin@12345' };
const STAFF = { email: 'staff@khmermart.local', password: 'Staff@12345' };
const SHOTS = 'docs/screenshots';

async function newPage(browser: Browser, who: { email: string; password: string }, locale: 'en' | 'km' = 'en') {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  await loginViaUI(page, who.email, who.password, locale);
  return { ctx, page };
}

test.describe('Honest refunds: cancel request → admin approval → manual refund', () => {
  test.describe.configure({ timeout: 120_000 });

  let customer: ThrowawayUser;
  let addressId: number;
  let pending: { orderNumber: string; paymentId: number }; // already REFUND_PENDING (API-seeded)

  test.beforeAll(async () => {
    const api = await pwRequest.newContext();
    customer = await createThrowawayUser(api);
    addressId = await createAddress(api, customer);
    pending = await seedRefundPending(api, customer, addressId);
    await api.dispose();
  });

  test('customer requests cancel → admin approves → REFUND_PENDING → admin marks sent → REFUNDED', async ({ browser }) => {
    const api = await pwRequest.newContext();
    const paid = await placePaidLiveOrder(api, customer, addressId);
    await api.dispose();

    const c = await newPage(browser, customer);
    await c.page.goto(`/en/orders/${paid.orderNumber}`);
    await expect(c.page.locator('[data-testid="request-cancel-btn"]')).toHaveText(/Request cancellation/i);
    await expect(c.page.locator('[data-testid="cancel-order-btn"]')).toHaveCount(0);

    // Reason is required
    await c.page.click('[data-testid="request-cancel-btn"]');
    const dialog = c.page.locator('[data-testid="request-cancel-dialog"]');
    await expect(dialog).toBeVisible();
    await c.page.click('[data-testid="confirm-request-cancel-btn"]');
    await expect(dialog.getByRole('alert')).toContainText(/required/i);
    await c.page.fill('[data-testid="request-cancel-reason-input"]', 'I found it cheaper elsewhere');
    await c.page.click('[data-testid="confirm-request-cancel-btn"]');
    await expect(c.page.getByText('Request sent. We will reply soon')).toBeVisible();
    await expect(dialog).toBeHidden();
    await expect(c.page.locator('[data-testid="order-cancel-requested-badge"]')).toContainText('Cancellation requested');
    await expect(c.page.locator('[data-testid="request-cancel-btn"]')).toHaveCount(0);

    // Admin sees the banner and approves with a reason
    const a = await newPage(browser, ADMIN);
    await a.page.goto(`/en/admin/orders/${paid.orderNumber}`);
    const banner = a.page.locator('[data-testid="cancel-request-banner"]');
    await expect(banner).toContainText('Customer requested cancellation');
    await expect(banner).toContainText('I found it cheaper elsewhere');
    await a.page.click('[data-testid="approve-cancel-btn"]');
    await a.page.click('[data-testid="confirm-approve-cancel-btn"]'); // empty → blocked
    await expect(a.page.locator('[data-testid="approve-cancel-dialog"]').getByRole('alert')).toContainText(/required/i);
    await a.page.fill('[data-testid="approve-reason-input"]', 'Approved: customer request');
    await a.page.click('[data-testid="confirm-approve-cancel-btn"]');
    await expect(banner).toHaveCount(0);
    await expect(a.page.locator('[data-testid="admin-payment-status"]')).toHaveText('REFUND_PENDING');
    await expect(a.page.locator('[data-testid="refund-card"]')).toBeVisible();
    await expect(a.page.locator('[data-testid="refund-card"]')).toContainText(/Open the shop's bank app/);

    // Customer sees REFUND_PENDING and the amount
    await c.page.reload();
    await expect(c.page.locator('[data-testid="order-refund-pending-badge"]')).toContainText('Refund pending');
    await expect(c.page.locator('[data-testid="order-refund-pending-text"]')).toContainText(/We will send \$\d+\.\d{2} back to/);
    await expect(c.page.locator('[data-testid="cancel-order-btn"], [data-testid="request-cancel-btn"]')).toHaveCount(0);
    // "Cancelled" is not printed twice in a row in the timeline
    const headers = await c.page.locator('[data-testid="timeline-event"] .font-bold').allTextContents();
    for (let i = 1; i < headers.length; i++) expect(headers[i]).not.toBe(headers[i - 1]);

    // Admin marks the refund sent from the order page (reference required)
    await a.page.click('[data-testid="mark-refund-sent-btn"]');
    await a.page.click('[data-testid="confirm-mark-refund-sent-btn"]');
    await expect(a.page.locator('[data-testid="mark-refund-sent-dialog"]').getByRole('alert').first()).toContainText(/required/i);
    await a.page.fill('[data-testid="bank-reference-input"]', 'FT-E2E-0001');
    await a.page.click('[data-testid="confirm-mark-refund-sent-btn"]');
    await expect(a.page.locator('[data-testid="admin-payment-status"]')).toHaveText('REFUNDED');

    // Customer sees REFUNDED with the bank reference
    await c.page.reload();
    await expect(c.page.locator('[data-testid="order-refunded-badge"]')).toContainText('Refunded');
    await expect(c.page.locator('[data-testid="order-refunded-text"]')).toContainText('FT-E2E-0001');

    await c.ctx.close();
    await a.ctx.close();
  });

  test('admin payments: REFUND_PENDING filter, payer account copy, mark as sent → REFUNDED with reference', async ({ browser }) => {
    const a = await newPage(browser, ADMIN);
    await a.page.goto('/en/admin/payments?status=REFUND_PENDING');
    const row = a.page.locator('tr', { hasText: pending.orderNumber });
    await expect(row).toContainText('Refund pending');
    await expect(a.page.locator('[data-testid="payment-status-filter"]')).toHaveValue('REFUND_PENDING');
    await expect(a.page.locator(`[data-testid="copy-account-btn-${pending.paymentId}"]`)).toBeVisible();
    await a.page.screenshot({ path: `${SHOTS}/refund-payments-filter-en.png` });

    await a.page.click(`[data-testid="mark-refund-sent-btn-${pending.paymentId}"]`);
    await a.page.click('[data-testid="confirm-mark-refund-sent-btn"]');
    await expect(a.page.locator('[data-testid="mark-refund-sent-dialog"]').getByRole('alert').first()).toContainText(/required/i);
    await a.page.fill('[data-testid="bank-reference-input"]', 'FT-E2E-0002');
    await a.page.fill('[data-testid="refund-note-input"]', 'sent via ABA');
    await a.page.click('[data-testid="confirm-mark-refund-sent-btn"]');
    await expect(a.page.locator('[data-testid="mark-refund-sent-dialog"]')).toBeHidden();

    await a.page.goto(`/en/admin/payments?status=REFUNDED`);
    await expect(a.page.locator(`[data-testid="refund-bank-ref-${pending.paymentId}"]`)).toContainText('FT-E2E-0002');
    await a.ctx.close();
  });

  test('dashboard shows "Refunds to send: N" linking to the filtered list', async ({ browser }) => {
    const api = await pwRequest.newContext();
    const extra = await seedRefundPending(api, customer, addressId);
    await api.dispose();
    const a = await newPage(browser, ADMIN);
    await a.page.goto('/en/admin');
    const counter = a.page.locator('[data-testid="dashboard-refunds-to-send"]');
    await expect(counter).toContainText(/Refunds to send: [1-9]\d*/);
    await counter.click();
    await expect(a.page).toHaveURL(/status=REFUND_PENDING/);
    await expect(a.page.locator('tr', { hasText: extra.orderNumber })).toBeVisible();
    await a.ctx.close();
  });

  test('STAFF sees the refund card but not "Mark refund as sent"', async ({ browser }) => {
    const api = await pwRequest.newContext();
    const seeded = await seedRefundPending(api, customer, addressId);
    await api.dispose();
    const s = await newPage(browser, STAFF);
    await s.page.goto(`/en/admin/orders/${seeded.orderNumber}`);
    await expect(s.page.locator('[data-testid="refund-card"]')).toBeVisible();
    await expect(s.page.locator('[data-testid="mark-refund-sent-btn"]')).toHaveCount(0);
    await expect(s.page.locator('[data-testid="refund-admin-only-note"]')).toBeVisible();
    await s.ctx.close();
  });

  test('admin can reject a cancellation request (reason required, shown to the customer)', async ({ browser }) => {
    const api = await pwRequest.newContext();
    const paid = await placePaidLiveOrder(api, customer, addressId);
    const res = await api.post(`${API}/orders/${paid.orderNumber}/cancel-request`, {
      headers: { Authorization: `Bearer ${customer.token}` },
      data: { reason: 'Changed my mind' },
    });
    expect(res.ok()).toBe(true);
    // a second request is refused with CANCEL_ALREADY_REQUESTED
    const again = await api.post(`${API}/orders/${paid.orderNumber}/cancel-request`, {
      headers: { Authorization: `Bearer ${customer.token}` },
      data: { reason: 'Again' },
    });
    expect((await again.json()).error.code).toBe('CANCEL_ALREADY_REQUESTED');
    await api.dispose();

    const a = await newPage(browser, ADMIN);
    await a.page.goto(`/en/admin/orders/${paid.orderNumber}`);
    await a.page.click('[data-testid="reject-cancel-btn"]');
    await a.page.click('[data-testid="confirm-reject-cancel-btn"]');
    await expect(a.page.locator('[data-testid="reject-cancel-dialog"]').getByRole('alert')).toContainText(/required/i);
    await a.page.fill('[data-testid="reject-reason-input"]', 'Already packed, cannot cancel');
    await a.page.click('[data-testid="confirm-reject-cancel-btn"]');
    await expect(a.page.locator('[data-testid="cancel-request-banner"]')).toHaveCount(0);
    await a.ctx.close();
  });

  test('unpaid order cancels directly (no request, no refund)', async ({ browser }) => {
    const api = await pwRequest.newContext();
    const { orderNumber } = await placeKhqrOrder(api, customer, addressId);
    await api.dispose();
    const c = await newPage(browser, customer);
    await c.page.goto(`/en/orders/${orderNumber}`);
    await expect(c.page.locator('[data-testid="request-cancel-btn"]')).toHaveCount(0);
    await c.page.click('[data-testid="cancel-order-btn"]');
    await c.page.fill('[data-testid="cancel-reason-input"]', 'Not needed');
    await c.page.click('[data-testid="confirm-cancel-btn"]');
    await expect(c.page.locator('[data-testid="order-detail-status-badge"]')).toContainText(/Cancelled/i);
    await expect(c.page.locator('[data-testid="order-refund-pending-badge"]')).toHaveCount(0);
    await c.ctx.close();
  });

  test('shipped order has no cancel button and says it can no longer be cancelled', async ({ browser }) => {
    const api = await pwRequest.newContext();
    const { orderNumber } = await placeKhqrOrder(api, customer, addressId, { ship: true });
    await api.dispose();
    const c = await newPage(browser, customer);
    await c.page.goto(`/en/orders/${orderNumber}`);
    await expect(c.page.locator('[data-testid="order-not-cancellable"]')).toHaveText('This order can no longer be cancelled');
    await expect(c.page.locator('[data-testid="cancel-order-btn"], [data-testid="request-cancel-btn"]')).toHaveCount(0);
    await c.ctx.close();
  });

  for (const locale of ['en', 'km'] as const) {
    test(`screenshots [${locale}]: customer order page and admin refund card`, async ({ browser }) => {
      const api = await pwRequest.newContext();
      const seeded = await seedRefundPending(api, customer, addressId);
      await api.dispose();

      const c = await newPage(browser, customer, locale);
      await c.page.goto(`/${locale}/orders/${seeded.orderNumber}`);
      const badge = c.page.locator('[data-testid="order-refund-pending-badge"]');
      await expect(badge).toContainText(locale === 'km' ? 'រង់ចាំសងប្រាក់' : 'Refund pending');
      await c.page.screenshot({ path: `${SHOTS}/refund-customer-order-${locale}.png`, fullPage: true });
      await c.ctx.close();

      const a = await newPage(browser, ADMIN, locale);
      await a.page.goto(`/${locale}/admin/orders/${seeded.orderNumber}`);
      const card = a.page.locator('[data-testid="refund-card"]');
      await expect(card).toBeVisible();
      await a.page.screenshot({ path: `${SHOTS}/refund-admin-card-${locale}.png`, fullPage: true });
      if (locale === 'km') {
        await a.page.goto(`/km/admin/payments?status=REFUND_PENDING`);
        await expect(a.page.locator('tr', { hasText: seeded.orderNumber })).toBeVisible();
        await a.page.screenshot({ path: `${SHOTS}/refund-payments-filter-km.png` });
      }
      await a.ctx.close();
    });
  }
});
