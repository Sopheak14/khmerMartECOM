import type { OrderDTO, OrderTimelineEvent, PaymentStatus, RefundDTO } from '@/types/api';

/** Tone of a status chip; the page maps it to colours. */
export type StatusTone = 'orange' | 'green' | 'red' | 'amber' | 'purple' | 'neutral';

/** Payment-status chip mapping shared by the admin payments list and the order pages. */
export function paymentStatusTone(status: PaymentStatus | string): StatusTone {
  switch (status) {
    case 'PAID':
      return 'green';
    case 'PENDING':
      return 'amber';
    case 'REFUND_PENDING':
      return 'orange';
    case 'REFUNDED':
    case 'PARTIALLY_REFUNDED':
      return 'purple';
    case 'FAILED':
    case 'EXPIRED':
      return 'red';
    default:
      return 'neutral';
  }
}

export const TONE_CLASSES: Record<StatusTone, string> = {
  orange: 'bg-orange-500/10 text-orange-800 dark:text-orange-400 border-orange-500/30',
  green: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20',
  red: 'bg-red-500/10 text-red-700 dark:text-red-400 border-red-500/20',
  amber: 'bg-amber-500/10 text-amber-800 dark:text-amber-400 border-amber-500/20',
  purple: 'bg-purple-500/10 text-purple-700 dark:text-purple-400 border-purple-500/20',
  neutral: 'bg-muted/15 text-muted border-border',
};

export type CustomerCancelAction = 'cancel' | 'request' | 'requested' | 'locked' | 'none';

/**
 * What the customer may do about cancelling.
 * - unpaid order → cancel directly; paid & not shipped → ask for cancellation;
 * - already asked → nothing to press; shipped or later → "can no longer be cancelled".
 */
export function customerCancelAction(
  order: Pick<OrderDTO, 'status' | 'paymentStatus' | 'cancelRequested'>,
): CustomerCancelAction {
  switch (order.status) {
    case 'PENDING_PAYMENT':
      return 'cancel';
    case 'CONFIRMED':
    case 'PROCESSING':
      if (order.cancelRequested) return 'requested';
      return order.paymentStatus === 'PAID' ? 'request' : 'cancel';
    case 'SHIPPED':
    case 'OUT_FOR_DELIVERY':
    case 'DELIVERED':
      return 'locked';
    default:
      return 'none';
  }
}

export interface RefundInfo {
  amountCents?: number;
  payerAccount?: string;
  bankReference?: string;
}

/** "Refund pending: $0.15 to <payerAccount> (manual bank transfer)" / "Refunded: $0.15 · bank ref <ref>" */
const PENDING_RE = /Refund pending:\s*\$?([\d,]+(?:\.\d+)?)\s+to\s+(.+?)\s*(?:\(manual[^)]*\))?\s*$/i;
const DONE_RE = /Refunded:\s*\$?([\d,]+(?:\.\d+)?)(?:\s*[·•-]\s*bank ref\s+(.+?))?\s*$/i;

function toCents(v: string): number {
  return Math.round(parseFloat(v.replace(/,/g, '')) * 100);
}

/** Refund facts for an order: the DTO's refund if the API sends one, otherwise read from the timeline wording. */
export function extractRefundInfo(order: { refund?: RefundDTO | null; timeline?: OrderTimelineEvent[] }): RefundInfo {
  const info: RefundInfo = {};
  if (order.refund) {
    info.amountCents = order.refund.amountCents;
    info.payerAccount = order.refund.payerAccount || undefined;
    info.bankReference = order.refund.bankReference || undefined;
  }
  for (const evt of order.timeline ?? []) {
    const note = evt.note;
    if (!note) continue;
    const pending = PENDING_RE.exec(note);
    if (pending) {
      info.amountCents ??= toCents(pending[1]);
      info.payerAccount ??= pending[2];
    }
    const done = DONE_RE.exec(note);
    if (done) {
      info.amountCents ??= toCents(done[1]);
      if (done[2]) info.bankReference ??= done[2];
    }
  }
  return info;
}

export interface TimelineRow {
  event: OrderTimelineEvent;
  /** false when the previous row already shows this status, so "Cancelled" is not printed twice in a row. */
  showStatus: boolean;
}

export function timelineRows(events: OrderTimelineEvent[] | undefined): TimelineRow[] {
  const rows: TimelineRow[] = [];
  let prev: string | undefined;
  for (const event of events ?? []) {
    const hasNote = Boolean(event.note);
    rows.push({ event, showStatus: event.status !== prev || !hasNote });
    prev = event.status;
  }
  return rows;
}

/** Error codes added by the cancel/refund flow; each has an `errors.<CODE>` message in en + km. */
export const REFUND_ERROR_CODES = [
  'CANCEL_NOT_ALLOWED',
  'CANCEL_ALREADY_REQUESTED',
  'CANCEL_NOT_REQUESTED',
  'INVALID_REFUND_STATE',
] as const;

/** Maps an API error to an `errors.*` message key; falls back to the server's message, then a generic one. */
export function errorMessageKey(err: unknown): string | null {
  const code = (err as { code?: string } | null)?.code;
  return code && (REFUND_ERROR_CODES as readonly string[]).includes(code) ? code : null;
}

export type FieldError = 'required' | 'tooShort' | 'tooLong';

export interface FieldRule {
  required?: boolean;
  minLength?: number;
  maxLength?: number;
}

export function validateField(value: string, rule: FieldRule): FieldError | null {
  const v = value.trim();
  if (rule.required && v.length === 0) return 'required';
  if (v.length > 0 && rule.minLength && v.length < rule.minLength) return 'tooShort';
  if (rule.maxLength && v.length > rule.maxLength) return 'tooLong';
  return null;
}

export const REASON_RULE: FieldRule = { required: true, maxLength: 500 };
export const BANK_REFERENCE_RULE: FieldRule = { required: true, minLength: 3, maxLength: 64 };
