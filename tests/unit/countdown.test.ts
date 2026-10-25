import { describe, it, expect } from 'vitest';
import { calculateKhqrCountdown } from '@/lib/khqrCountdown';

describe('calculateKhqrCountdown', () => {
  it('formats minutes and seconds correctly for future time (> 1 min)', () => {
    const now = 1000000;
    const expiresAt = now + 9 * 60 * 1000 + 45 * 1000; // 9m 45s later
    const result = calculateKhqrCountdown(expiresAt, now);

    expect(result.minutes).toBe(9);
    expect(result.seconds).toBe(45);
    expect(result.formatted).toBe('09:45');
    expect(result.isExpired).toBe(false);
    expect(result.isUnderOneMinute).toBe(false);
  });

  it('detects under one minute correctly (< 60s)', () => {
    const now = 1000000;
    const expiresAt = now + 42 * 1000; // 42s later
    const result = calculateKhqrCountdown(expiresAt, now);

    expect(result.minutes).toBe(0);
    expect(result.seconds).toBe(42);
    expect(result.formatted).toBe('00:42');
    expect(result.isExpired).toBe(false);
    expect(result.isUnderOneMinute).toBe(true);
  });

  it('marks as expired when diff <= 0', () => {
    const now = 1000000;
    const expiresAt = now - 5000; // 5s in past
    const result = calculateKhqrCountdown(expiresAt, now);

    expect(result.minutes).toBe(0);
    expect(result.seconds).toBe(0);
    expect(result.formatted).toBe('00:00');
    expect(result.isExpired).toBe(true);
    expect(result.isUnderOneMinute).toBe(false);
  });

  it('handles exact expiration time', () => {
    const now = 1000000;
    const expiresAt = now;
    const result = calculateKhqrCountdown(expiresAt, now);

    expect(result.formatted).toBe('00:00');
    expect(result.isExpired).toBe(true);
  });

  it('handles invalid date input gracefully', () => {
    const result = calculateKhqrCountdown('invalid-date-string');
    expect(result.isExpired).toBe(true);
    expect(result.formatted).toBe('00:00');
  });
});
