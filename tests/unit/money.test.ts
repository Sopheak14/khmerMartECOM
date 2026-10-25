import { describe, it, expect } from 'vitest';
import {
  formatUsd,
  formatKhr,
  calculateKhr,
  formatKhrRaw,
  DEFAULT_KHR_RATE,
  calculateCouponDiscountCents,
  translatePromotionLabel,
} from '@/lib/money';

describe('Money Utilities (USD & KHR calculations)', () => {
  it('correctly formats USD cents to dollar string with thousands separators', () => {
    expect(formatUsd(99900)).toBe('$999.00');
    expect(formatUsd(119900)).toBe('$1,199.00');
    expect(formatUsd(129900)).toBe('$1,299.00');
    expect(formatUsd(3000)).toBe('$30.00');
    expect(formatUsd(0)).toBe('$0.00');
    expect(formatUsd(1250)).toBe('$12.50');
  });

  it('calculates Cambodian Riel equivalent with given exchange rate', () => {
    // 3000 cents ($30.00) * 4100 = 123,000 KHR
    expect(calculateKhr(3000, 4100)).toBe(123000);
    // 99900 cents ($999.00) * 4100 = 4,095,900 KHR
    expect(calculateKhr(99900, 4100)).toBe(4095900);
    // 89900 cents ($899.00) * 4100 = 3,685,900 KHR
    expect(calculateKhr(89900, 4100)).toBe(3685900);
  });

  it('uses DEFAULT_KHR_RATE (4100) when rate is not provided', () => {
    expect(calculateKhr(100)).toBe(DEFAULT_KHR_RATE);
  });

  it('formats KHR string with single ≈ prefix and ៛ suffix (Item 2 specification)', () => {
    // $899.00 at 4100 KHR rate
    const khrFormatted = formatKhr(89900, 4100);
    expect(khrFormatted).toBe('≈ 3,685,900 ៛');
    expect(khrFormatted.startsWith('≈ ')).toBe(true);
    expect(khrFormatted.includes('≈≈')).toBe(false);
    expect(khrFormatted.endsWith(' ៛')).toBe(true);
  });

  it('formats raw KHR amount correctly with ៛ suffix', () => {
    const formatted = formatKhrRaw(123000);
    expect(formatted).toBe('123,000 ៛');
    const withPrefix = formatKhrRaw(3685900, '≈ ');
    expect(withPrefix).toBe('≈ 3,685,900 ៛');
  });

  it('correctly calculates coupon discount using: discountCents - sum(promotionDiscounts.amountCents)', () => {
    // Real CartDTO numbers from test log:
    // Total discount = $107.10 (10710 cents)
    // Flash sale promo line = $102.10 (10210 cents)
    // Real coupon discount should be exactly $5.00 (500 cents)
    const cartDtoLog = {
      discountCents: 10710,
      promotionDiscounts: [
        { label: 'Flash Sale Discount', amountCents: 10210 },
      ],
    };

    const couponCents = calculateCouponDiscountCents(
      cartDtoLog.discountCents,
      cartDtoLog.promotionDiscounts
    );

    expect(couponCents).toBe(500);
    expect(formatUsd(couponCents)).toBe('$5.00');
  });

  it('translates promotion labels into Khmer accurately', () => {
    expect(translatePromotionLabel('Flash Sale Discount', 'km')).toBe('ការបញ្ចុះតម្លៃ Flash Sale');
    expect(translatePromotionLabel('Seasonal Promotion (20%)', 'km')).toBe('ការបញ្ចុះតម្លៃតាមរដូវកាល (20%)');
    expect(translatePromotionLabel('Buy 1 Get 1 Free', 'km')).toBe('ទិញ ១ ថែម ១ ឥតគិតថ្លៃ');
    // Keeps English unchanged
    expect(translatePromotionLabel('Flash Sale Discount', 'en')).toBe('Flash Sale Discount');
  });
});
