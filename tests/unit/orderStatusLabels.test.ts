import { describe, it, expect } from 'vitest';
import en from '../../messages/en.json';
import km from '../../messages/km.json';

const ORDER_STATUSES = [
  'PENDING_PAYMENT',
  'CONFIRMED',
  'PROCESSING',
  'SHIPPED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELLED',
  'PAYMENT_EXPIRED',
  'REFUNDED',
] as const;

describe.each([
  ['en', en, 'Out for delivery'],
  ['km', km, 'កំពុងដឹកជញ្ជូន'],
] as const)('order status labels (%s)', (_locale, messages, outForDelivery) => {
  const labels = messages.orders.status as Record<string, string>;

  it('has a label for every OrderStatus', () => {
    for (const status of ORDER_STATUSES) expect(labels[status], status).toBeTruthy();
  });

  it('gives every status its own label', () => {
    expect(new Set(ORDER_STATUSES.map((s) => labels[s])).size).toBe(ORDER_STATUSES.length);
  });

  it('labels OUT_FOR_DELIVERY separately from SHIPPED', () => {
    expect(labels.OUT_FOR_DELIVERY).toBe(outForDelivery);
    expect(labels.OUT_FOR_DELIVERY).not.toBe(labels.SHIPPED);
  });

  it('uses the same OUT_FOR_DELIVERY label on the tracking page', () => {
    expect((messages.track.status as Record<string, string>).OUT_FOR_DELIVERY).toBe(outForDelivery);
  });
});
