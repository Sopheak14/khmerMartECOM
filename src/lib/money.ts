// Currency & Money formatting utilities for KhmerMart

export const DEFAULT_KHR_RATE = 4100;

/**
 * Format USD cents into standard dollar representation, e.g. 99900 -> "$999.00"
 */
export function formatUsd(cents: number): string {
  if (isNaN(cents)) return "$0.00";
  const dollars = cents / 100;
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
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
 * Format USD cents into Cambodian Riel string, e.g. 3000 -> "≈ ៛123,000"
 */
export function formatKhr(cents: number, rate: number = DEFAULT_KHR_RATE, prefix: string = "≈ "): string {
  const khr = calculateKhr(cents, rate);
  const formatted = new Intl.NumberFormat("km-KH", {
    style: "currency",
    currency: "KHR",
    maximumFractionDigits: 0,
  }).format(khr);

  return `${prefix}${formatted}`;
}

/**
 * Format a raw KHR amount (riel) directly, e.g. 123000 -> "៛123,000"
 */
export function formatKhrRaw(amount: number): string {
  if (isNaN(amount)) return "៛0";
  return new Intl.NumberFormat("km-KH", {
    style: "currency",
    currency: "KHR",
    maximumFractionDigits: 0,
  }).format(amount);
}
