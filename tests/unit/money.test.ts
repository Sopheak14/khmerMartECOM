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
  });
});
