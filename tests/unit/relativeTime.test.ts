import { describe, it, expect } from 'vitest';
import { formatRelativeTime, toKhmerDigits } from '@/lib/relativeTime';

describe('formatRelativeTime', () => {
  const baseNow = new Date('2026-10-25T12:00:00Z');

  it('converts digits to Khmer numerals correctly', () => {
    expect(toKhmerDigits(5)).toBe('៥');
    expect(toKhmerDigits(1234567890)).toBe('១២៣៤៥៦៧៨៩០');
  });

  it('formats English relative time correctly', () => {
    // 30 seconds ago
    const thirtySecs = new Date(baseNow.getTime() - 30 * 1000);
    expect(formatRelativeTime(thirtySecs, 'en', baseNow)).toBe('just now');

    // 1 minute ago
    const oneMin = new Date(baseNow.getTime() - 60 * 1000);
    expect(formatRelativeTime(oneMin, 'en', baseNow)).toBe('1 minute ago');

    // 5 minutes ago
    const fiveMins = new Date(baseNow.getTime() - 5 * 60 * 1000);
    expect(formatRelativeTime(fiveMins, 'en', baseNow)).toBe('5 minutes ago');

    // 1 hour ago
    const oneHour = new Date(baseNow.getTime() - 60 * 60 * 1000);
    expect(formatRelativeTime(oneHour, 'en', baseNow)).toBe('1 hour ago');

    // 3 hours ago
    const threeHours = new Date(baseNow.getTime() - 3 * 60 * 60 * 1000);
    expect(formatRelativeTime(threeHours, 'en', baseNow)).toBe('3 hours ago');

    // 1 day ago
    const oneDay = new Date(baseNow.getTime() - 24 * 60 * 60 * 1000);
    expect(formatRelativeTime(oneDay, 'en', baseNow)).toBe('1 day ago');

    // 4 days ago
    const fourDays = new Date(baseNow.getTime() - 4 * 24 * 60 * 60 * 1000);
    expect(formatRelativeTime(fourDays, 'en', baseNow)).toBe('4 days ago');
  });

  it('formats Khmer relative time correctly', () => {
    // 30 seconds ago
    const thirtySecs = new Date(baseNow.getTime() - 30 * 1000);
    expect(formatRelativeTime(thirtySecs, 'km', baseNow)).toBe('អម្បាញ់មិញ');

    // 1 minute ago
    const oneMin = new Date(baseNow.getTime() - 60 * 1000);
    expect(formatRelativeTime(oneMin, 'km', baseNow)).toBe('១ នាទីមុន');

    // 5 minutes ago (as required: "5 minutes ago" / "៥ នាទីមុន")
    const fiveMins = new Date(baseNow.getTime() - 5 * 60 * 1000);
    expect(formatRelativeTime(fiveMins, 'km', baseNow)).toBe('៥ នាទីមុន');

    // 1 hour ago
    const oneHour = new Date(baseNow.getTime() - 60 * 60 * 1000);
    expect(formatRelativeTime(oneHour, 'km', baseNow)).toBe('១ ម៉ោងមុន');

    // 3 hours ago
    const threeHours = new Date(baseNow.getTime() - 3 * 60 * 60 * 1000);
    expect(formatRelativeTime(threeHours, 'km', baseNow)).toBe('៣ ម៉ោងមុន');

    // 1 day ago
    const oneDay = new Date(baseNow.getTime() - 24 * 60 * 60 * 1000);
    expect(formatRelativeTime(oneDay, 'km', baseNow)).toBe('១ ថ្ងៃមុន');

    // 4 days ago
    const fourDays = new Date(baseNow.getTime() - 4 * 24 * 60 * 60 * 1000);
    expect(formatRelativeTime(fourDays, 'km', baseNow)).toBe('៤ ថ្ងៃមុន');
  });
});
