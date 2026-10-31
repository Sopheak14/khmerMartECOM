import { APIRequestContext, expect } from '@playwright/test';
import { createThrowawayUser, ThrowawayUser } from './auth';
import { API, COCONUT_OIL } from '../fixtures/catalog';


export async function adminToken(request: APIRequestContext): Promise<string> {
  const res = await request.post(`${API}/auth/login`, {
    data: { email: 'admin@khmermart.local', password: 'Admin@12345' },
  });
  expect(res.ok()).toBe(true);
  return (await res.json()).data.accessToken;
}

export async function createAddress(request: APIRequestContext, user: ThrowawayUser): Promise<number> {
  const res = await request.post(`${API}/users/me/addresses`, {
    headers: { Authorization: `Bearer ${user.token}` },
    data: {
      label: 'Home',
      recipientName: user.fullName,
      phone: user.phone,
      line1: 'Building 12, Russian Blvd',
      district: 'Toul Kork',
      province: 'Phnom Penh',
      postalCode: '12000',
      isDefault: true,
    },
  });
  return (await res.json()).data.id;
}

/** Places a KHQR order for the user via the API; optionally pays and ships it. Returns order/tracking numbers. */
export async function placeKhqrOrder(
  request: APIRequestContext,
  user: ThrowawayUser,
  addressId: number,
  opts: { pay?: boolean; ship?: boolean; productId?: number; variantId?: number; coupon?: string } = {}
) {
  const auth = { Authorization: `Bearer ${user.token}` };
  await request.post(`${API}/cart/items`, {
    headers: auth,
    data: { productId: opts.productId ?? COCONUT_OIL.id, variantId: opts.productId ? opts.variantId : undefined, quantity: 1 },
  });
  if (opts.coupon) {
    await request.post(`${API}/cart/coupon`, { headers: auth, data: { code: opts.coupon } });
  }
  const checkout = await request.post(`${API}/checkout`, {
    headers: { ...auth, 'Idempotency-Key': `idem-${Date.now()}-${Math.random().toString(36).slice(2, 7)}` },
    data: { addressId, shippingMethodCode: 'STANDARD', paymentMethod: 'KHQR' },
  });
  const checkoutJson = await checkout.json();
  if (!checkoutJson.data?.order) throw new Error(`Checkout failed: ${JSON.stringify(checkoutJson)}`);
  const orderNumber: string = checkoutJson.data.order.orderNumber;
  const pay = await request.post(`${API}/payments/khqr/${orderNumber}`, { headers: auth });
  const paymentId = (await pay.json()).data.paymentId;
  let trackingNumber: string | undefined;
  if (opts.pay || opts.ship) {
    await request.post(`${API}/payments/${paymentId}/simulate-paid`, { headers: auth });
  }
  if (opts.ship) {
    const token = await adminToken(request);
    const ship = await request.post(`${API}/admin/orders/${orderNumber}/ship`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { carrier: 'KhmerMart Express' },
    });
    trackingNumber = (await ship.json()).data?.trackingNumber;
  }
  return { orderNumber, paymentId, trackingNumber };
}


export { createThrowawayUser };

/**
 * Tracking number of an already-shipped order (the public /track page needs no login). When the database has none
 * (fresh reset), it creates one itself: a throwaway customer checks out via the API and the order is paid and shipped.
 * No seeded customer, staff or admin data is modified (the admin token is only used to list and to ship).
 */
export async function findShippedTrackingNumber(request: APIRequestContext): Promise<string> {
  const token = await adminToken(request);
  const res = await request.get(`${API}/admin/orders`, {
    headers: { Authorization: `Bearer ${token}` },
    params: { status: 'SHIPPED', size: 1 },
  });
  const existing = (await res.json()).data?.[0]?.trackingNumber;
  if (existing) return existing;

  const user = await createThrowawayUser(request);
  const addressId = await createAddress(request, user);
  const { trackingNumber } = await placeKhqrOrder(request, user, addressId, { ship: true });
  expect(trackingNumber, 'a shipped order must exist').toBeTruthy();
  return trackingNumber as string;
}

export async function cancelOrder(request: APIRequestContext, user: ThrowawayUser, orderNumber: string) {
  await request.post(`${API}/orders/${orderNumber}/cancel`, { headers: { Authorization: `Bearer ${user.token}` } });
}
