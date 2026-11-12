import { describe, it, expect } from 'vitest';
import en from '../../messages/en.json';
import km from '../../messages/km.json';
import {
  customerCancelAction,
  errorMessageKey,
  extractRefundInfo,
  paymentStatusTone,
  timelineRows,
  validateField,
  REASON_RULE,
  BANK_REFERENCE_RULE,
  REFUND_ERROR_CODES,
} from '@/lib/refund';

describe('payment status badge mapping', () => {
  it('REFUND_PENDING is orange, REFUNDED is purple (admin) and PAID is green', () => {
    expect(paymentStatusTone('REFUND_PENDING')).toBe('orange');
    expect(paymentStatusTone('REFUNDED')).toBe('purple');
    expect(paymentStatusTone('PAID')).toBe('green');
    expect(paymentStatusTone('PENDING')).toBe('amber');
    expect(paymentStatusTone('FAILED')).toBe('red');
    expect(paymentStatusTone('SOMETHING_NEW')).toBe('neutral');
  });
});

describe('customerCancelAction', () => {
  const o = (status: any, paymentStatus: any, cancelRequested = false) => ({ status, paymentStatus, cancelRequested });
  it('unpaid orders cancel directly', () => {
    expect(customerCancelAction(o('PENDING_PAYMENT', 'PENDING'))).toBe('cancel');
  });
  it('paid confirmed/processing orders ask for cancellation', () => {
    expect(customerCancelAction(o('CONFIRMED', 'PAID'))).toBe('request');
    expect(customerCancelAction(o('PROCESSING', 'PAID'))).toBe('request');
  });
  it('a request already sent leaves no button', () => {
    expect(customerCancelAction(o('CONFIRMED', 'PAID', true))).toBe('requested');
  });
  it('shipped or later is locked; cancelled shows nothing', () => {
    for (const s of ['SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED']) expect(customerCancelAction(o(s, 'PAID'))).toBe('locked');
    expect(customerCancelAction(o('CANCELLED', 'REFUND_PENDING'))).toBe('none');
  });
});

describe('extractRefundInfo', () => {
  const t = (note: string) => ({ status: 'CANCELLED' as const, note, at: '2026-01-01T00:00:00Z' });
  it('reads amount and payer account from the pending wording', () => {
    const info = extractRefundInfo({ timeline: [t('Refund pending: $0.15 to chheang@aba (manual bank transfer)')] } as any);
    expect(info).toMatchObject({ amountCents: 15, payerAccount: 'chheang@aba' });
  });
  it('reads the bank reference from the refunded wording', () => {
    const info = extractRefundInfo({ timeline: [t('Refunded: $0.15 · bank ref FT123456')] } as any);
    expect(info).toMatchObject({ amountCents: 15, bankReference: 'FT123456' });
  });
  it('prefers the structured refund when present', () => {
    const info = extractRefundInfo({
      refund: { id: 1, amountCents: 99, payerAccount: 'x@y', status: 'COMPLETED', bankReference: 'REF1' },
      timeline: [t('Refund pending: $0.15 to other (manual bank transfer)')],
    } as any);
    expect(info).toEqual({ amountCents: 99, payerAccount: 'x@y', bankReference: 'REF1' });
  });
});

describe('extractRefundInfo (backend-refund-2)', () => {
  it('treats an absent refund key as no refund', () => {
    expect(extractRefundInfo({ timeline: [] } as any)).toEqual({});
  });
  it('keeps the masked payer and ignores null bankReference', () => {
    const info = extractRefundInfo({
      refund: { id: 1, amountCents: 15, payerAccount: '****bkrt', status: 'PENDING', bankReference: null, completedAt: null },
      timeline: [],
    } as any);
    expect(info).toEqual({ amountCents: 15, payerAccount: '****bkrt', bankReference: undefined });
  });
});

describe('timelineRows', () => {
  it('does not print the same status twice in a row', () => {
    const rows = timelineRows([
      { status: 'CONFIRMED', at: '1' },
      { status: 'CANCELLED', note: 'Cancelled by admin: x', at: '2' },
      { status: 'CANCELLED', note: 'Refund pending: $0.15 to a (manual bank transfer)', at: '3' },
    ]);
    expect(rows.map((r) => r.showStatus)).toEqual([true, true, false]);
  });
});

describe('validation rules', () => {
  it('reason is required (blank counts as empty) and capped at 500', () => {
    expect(validateField('', REASON_RULE)).toBe('required');
    expect(validateField('   ', REASON_RULE)).toBe('required');
    expect(validateField('changed my mind', REASON_RULE)).toBeNull();
    expect(validateField('x'.repeat(501), REASON_RULE)).toBe('tooLong');
  });
  it('bank reference is required, 3-64 chars', () => {
    expect(validateField('', BANK_REFERENCE_RULE)).toBe('required');
    expect(validateField('ab', BANK_REFERENCE_RULE)).toBe('tooShort');
    expect(validateField('FT123', BANK_REFERENCE_RULE)).toBeNull();
    expect(validateField('x'.repeat(65), BANK_REFERENCE_RULE)).toBe('tooLong');
  });
});

describe('error codes', () => {
  it('maps only the new refund codes', () => {
    expect(errorMessageKey({ code: 'CANCEL_ALREADY_REQUESTED' })).toBe('CANCEL_ALREADY_REQUESTED');
    expect(errorMessageKey({ code: 'NOT_FOUND' })).toBeNull();
    expect(errorMessageKey(new Error('x'))).toBeNull();
  });
  it.each([['en', en], ['km', km]] as const)('every new code has a message in %s', (_l, m) => {
    for (const code of REFUND_ERROR_CODES) expect((m.errors as Record<string, string>)[code], code).toBeTruthy();
  });
  it('en and km have the same refunds keys', () => {
    const keys = (o: any, p = ''): string[] =>
      Object.entries(o).flatMap(([k, v]) => (typeof v === 'object' ? keys(v, `${p}${k}.`) : [`${p}${k}`]));
    expect(keys(km.refunds).sort()).toEqual(keys(en.refunds).sort());
  });
});
