// Currency & Money formatting utilities for KhmerMart

export const DEFAULT_KHR_RATE = 4100;

/**
 * Format USD cents into standard dollar representation with thousands separators, e.g. 129900 -> "$1,299.00"
 */
export function formatUsd(cents: number): string {
  if (isNaN(cents)) return "$0.00";
  const dollars = cents / 100;
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    useGrouping: true,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(dollars);
}

/**
 * Calculate KHR riel equivalent from USD cents and exchange rate.
 * Formula: round(cents / 100 * khrRate)
 */
export function calculateKhr(cents: number, rate: number = DEFAULT_KHR_RATE): number {
  if (isNaN(cents)) return 0;
  return Math.round((cents / 100) * (rate > 0 ? rate : DEFAULT_KHR_RATE));
}

/**
 * Format USD cents into Cambodian Riel string, e.g. 89900 -> "≈ 3,685,900 ៛"
 * Standardized format used everywhere: single "≈ " prefix, commas as thousands separators, and " ៛" suffix.
 * Deterministic across Node.js SSR and Browser client environments to prevent hydration mismatches.
 */
export function formatKhr(cents: number, rate: number = DEFAULT_KHR_RATE, prefix: string = "≈ "): string {
  const khr = calculateKhr(cents, rate);
  const formattedNumber = new Intl.NumberFormat("en-US", {
    useGrouping: true,
    maximumFractionDigits: 0,
  }).format(khr);

  return `${prefix}${formattedNumber} ៛`;
}

/**
 * Format a raw KHR amount (riel) directly, e.g. 123000 -> "123,000 ៛" or with optional prefix "≈ 123,000 ៛"
 */
export function formatKhrRaw(amount: number, prefix: string = ""): string {
  if (isNaN(amount)) return "0 ៛";
  const formattedNumber = new Intl.NumberFormat("en-US", {
    useGrouping: true,
    maximumFractionDigits: 0,
  }).format(amount);

  return `${prefix}${formattedNumber} ៛`;
}

/**
 * Calculate net coupon discount:
 * Coupon line = discountCents - sum(promotionDiscounts.amountCents)
 */
export function calculateCouponDiscountCents(
  discountCents: number = 0,
  promotionDiscounts: { amountCents: number }[] = []
): number {
  const promoSum = (promotionDiscounts || []).reduce((sum, p) => sum + (p.amountCents || 0), 0);
  return Math.max(0, discountCents - promoSum);
}

/**
 * Translate backend promotion discount labels for Khmer locale
 */
export function translatePromotionLabel(label: string, locale: string = 'en'): string {
  if (locale !== 'km') return label;

  if (/^Flash Sale Discount$/i.test(label)) {
    return 'ការបញ្ចុះតម្លៃ Flash Sale';
  }
  const seasonalMatch = label.match(/^Seasonal Promotion \((\d+%)\)$/i);
  if (seasonalMatch) {
    return `ការបញ្ចុះតម្លៃតាមរដូវកាល (${seasonalMatch[1]})`;
  }
  if (/^Buy 1 Get 1 Free$/i.test(label) || /bogo/i.test(label)) {
    return 'ទិញ ១ ថែម ១ ឥតគិតថ្លៃ';
  }
  if (/flash sale/i.test(label)) {
    return 'ការបញ្ចុះតម្លៃ Flash Sale';
  }
  if (/seasonal/i.test(label)) {
    return 'ការបញ្ចុះតម្លៃតាមរដូវកាល';
  }
  return label;
}
