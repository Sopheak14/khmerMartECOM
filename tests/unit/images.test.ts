import { describe, it, expect } from 'vitest';
import { getImageUrl } from '@/lib/images';

describe('getImageUrl', () => {
  it('resolves /uploads paths against the uploads host', () => {
    expect(getImageUrl('/uploads/products/a.jpg')).toMatch(/\/uploads\/products\/a\.jpg$/);
  });
  it('returns empty for missing urls', () => {
    expect(getImageUrl(null)).toBe('');
    expect(getImageUrl(undefined)).toBe('');
  });
  it('treats the backend via.placeholder.com default as no image so the fallback renders', () => {
    expect(getImageUrl('https://via.placeholder.com/300')).toBe('');
    expect(getImageUrl('https://via.placeholder.com/600')).toBe('');
  });
  it('keeps other absolute urls', () => {
    expect(getImageUrl('https://images.unsplash.com/x.jpg')).toBe('https://images.unsplash.com/x.jpg');
  });
});
