import { describe, it, expect } from 'vitest';
import { getDiscountLines } from '@/lib/orderDiscounts';

const labels = { coupon: (code: string) => `Coupon (${code})`, discounts: 'Discounts' };

// Demo order: hero Standard $149.00, flash sale -$20.00, SAVE5 -$5.00, free shipping -> $124.00
const demo = {
  subtotalCents: 14900,
  discountCents: 2500,
  shippingCents: 0,
  totalCents: 12400,
  discountBreakdown: {
    promotions: [{ label: 'Flash Sale Discount', amountCents: 2000 }],
    coupon: { code: 'SAVE5', amountCents: 500 },
  },
};

const total = (o: typeof demo, lines: ReturnType<typeof getDiscountLines>) =>
  o.subtotalCents - lines.reduce((s, l) => s + l.amountCents, 0) + o.shippingCents;

describe('getDiscountLines', () => {
  it('lists one line per promotion plus the coupon, and subtotal - lines + shipping equals total', () => {
    const lines = getDiscountLines(demo, 'en', labels);
    expect(lines.map((l) => [l.label, l.amountCents])).toEqual([
      ['Flash Sale Discount', 2000],
      ['Coupon (SAVE5)', 500],
    ]);
    expect(total(demo, lines)).toBe(demo.totalCents);
  });

  it('translates promotion labels in Khmer', () => {
    const lines = getDiscountLines(demo, 'km', labels);
    expect(lines[0].label).toBe('ការបញ្ចុះតម្លៃ Flash Sale');
  });

  it('handles several promotions and no coupon', () => {
    const order = {
      subtotalCents: 700,
      discountCents: 350,
      shippingCents: 150,
      totalCents: 500,
      discountBreakdown: { promotions: [{ label: 'Buy 1 Get 1 Free', amountCents: 350 }], coupon: null },
    };
    const lines = getDiscountLines(order, 'en', labels);
    expect(lines).toHaveLength(1);
    expect(order.subtotalCents - lines.reduce((s, l) => s + l.amountCents, 0) + order.shippingCents).toBe(order.totalCents);
  });

  it('falls back to a single "Discounts" line when the breakdown is empty but discountCents > 0', () => {
    const lines = getDiscountLines(
      { discountCents: 2500, discountBreakdown: { promotions: [], coupon: null } },
      'en',
      labels
    );
    expect(lines).toEqual([{ key: 'total', kind: 'total', label: 'Discounts', amountCents: 2500 }]);
    expect(getDiscountLines({ discountCents: 2500 }, 'en', labels)).toHaveLength(1);
  });

  it('renders nothing when there is no discount at all', () => {
    expect(getDiscountLines({ discountCents: 0, discountBreakdown: { promotions: [], coupon: null } }, 'en', labels)).toEqual([]);
  });
});
