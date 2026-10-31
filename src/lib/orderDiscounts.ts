import { OrderDTO } from '@/types/api';
import { translatePromotionLabel } from '@/lib/money';

export interface DiscountLine {
  key: string;
  kind: 'promotion' | 'coupon' | 'total';
  label: string;
  amountCents: number;
}

/**
 * Discount rows for an order summary: one per promotion, a coupon row when a coupon was used, or a single
 * aggregate row when the order has no breakdown snapshot (old orders) but still carries a discount.
 */
export function getDiscountLines(
  order: Pick<OrderDTO, 'discountCents' | 'discountBreakdown'>,
  locale: string,
  labels: { coupon: (code: string) => string; discounts: string }
): DiscountLine[] {
  const promotions = order.discountBreakdown?.promotions ?? [];
  const coupon = order.discountBreakdown?.coupon ?? null;

  if (promotions.length === 0 && !coupon) {
    return order.discountCents > 0
      ? [{ key: 'total', kind: 'total', label: labels.discounts, amountCents: order.discountCents }]
      : [];
  }

  const lines: DiscountLine[] = promotions.map((p, i) => ({
    key: `promotion-${i}`,
    kind: 'promotion',
    label: translatePromotionLabel(p.label, locale),
    amountCents: p.amountCents,
  }));
  if (coupon) {
    lines.push({ key: 'coupon', kind: 'coupon', label: labels.coupon(coupon.code), amountCents: coupon.amountCents });
  }
  return lines;
}
