import { describe, it, expect } from 'vitest';
import { parseFeeInput } from '@/lib/shippingFee';

describe('parseFeeInput', () => {
  it('accepts non-negative amounts with up to two decimals', () => {
    expect(parseFeeInput('0')).toEqual({ ok: true, cents: 0 });
    expect(parseFeeInput('2')).toEqual({ ok: true, cents: 200 });
    expect(parseFeeInput('2.5')).toEqual({ ok: true, cents: 250 });
    expect(parseFeeInput(' 3.99 ')).toEqual({ ok: true, cents: 399 });
    expect(parseFeeInput('0.29')).toEqual({ ok: true, cents: 29 });
  });

  it('rejects negatives, 3+ decimals, and garbage', () => {
    expect(parseFeeInput('-1')).toEqual({ ok: false, error: 'negative' });
    expect(parseFeeInput('1.005')).toEqual({ ok: false, error: 'decimals' });
    expect(parseFeeInput('abc')).toEqual({ ok: false, error: 'invalid' });
    expect(parseFeeInput('1e3')).toEqual({ ok: false, error: 'invalid' });
    expect(parseFeeInput('.')).toEqual({ ok: false, error: 'invalid' });
  });

  it('requires a value unless optional', () => {
    expect(parseFeeInput('')).toEqual({ ok: false, error: 'required' });
    expect(parseFeeInput('  ', { optional: true })).toEqual({ ok: true, cents: null });
    expect(parseFeeInput('-5', { optional: true })).toEqual({ ok: false, error: 'negative' });
  });
});
