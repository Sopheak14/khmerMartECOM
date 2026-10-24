import { describe, it, expect } from 'vitest';
import { formatUsd, formatKhr, calculateKhr, formatKhrRaw, DEFAULT_KHR_RATE } from '@/lib/money';

describe('Money Utilities (USD & KHR calculations)', () => {
  it('correctly formats USD cents to dollar string', () => {
    expect(formatUsd(99900)).toBe('$999.00');
    expect(formatUsd(3000)).toBe('$30.00');
    expect(formatUsd(0)).toBe('$0.00');
    expect(formatUsd(1250)).toBe('$12.50');
  });

  it('calculates Cambodian Riel equivalent with given exchange rate', () => {
    // 3000 cents ($30.00) * 4100 = 123,000 KHR
    expect(calculateKhr(3000, 4100)).toBe(123000);
    // 99900 cents ($999.00) * 4100 = 4,095,900 KHR
    expect(calculateKhr(99900, 4100)).toBe(4095900);
  });

  it('uses DEFAULT_KHR_RATE (4100) when rate is not provided', () => {
    expect(calculateKhr(100)).toBe(DEFAULT_KHR_RATE);
  });

  it('formats KHR string with prefix and symbol', () => {
    const khrFormatted = formatKhr(3000, 4100);
    expect(khrFormatted).toContain('123,000');
    expect(khrFormatted).toContain('≈');
  });

  it('formats raw KHR amount correctly', () => {
    const formatted = formatKhrRaw(123000);
    expect(formatted).toContain('123,000');
  });
});
