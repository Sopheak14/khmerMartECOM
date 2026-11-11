-- V8: honest refunds. A KHQR/COD refund is a MANUAL transfer by a person, so the system must
-- not claim "REFUNDED" until a human records the bank reference.

-- ---- payments: how was it paid (MOCK | LIVE | COD)? payer = existing from_account_id ----
ALTER TABLE payments ADD COLUMN paid_mode VARCHAR(10);

UPDATE payments SET paid_mode = 'MOCK' WHERE bakong_hash LIKE 'MOCK-%';
UPDATE payments SET paid_mode = 'COD'
 WHERE paid_mode IS NULL AND method = 'COD' AND paid_at IS NOT NULL;
UPDATE payments SET paid_mode = 'LIVE'
 WHERE paid_mode IS NULL
   AND status IN ('PAID', 'REFUNDED', 'PARTIALLY_REFUNDED')
   AND method IN ('KHQR', 'BANK_TRANSFER')
   AND (bakong_hash IS NOT NULL OR method = 'BANK_TRANSFER');

-- ---- refunds: manual-transfer bookkeeping ----
ALTER TABLE refunds ADD COLUMN payer_account VARCHAR(100);
ALTER TABLE refunds ADD COLUMN requested_at TIMESTAMPTZ;
ALTER TABLE refunds ADD COLUMN approved_by BIGINT;
ALTER TABLE refunds ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE refunds ADD COLUMN completed_by BIGINT;
ALTER TABLE refunds ADD COLUMN bank_reference VARCHAR(64);
ALTER TABLE refunds ADD COLUMN note VARCHAR(500);

UPDATE refunds SET requested_at = created_at, approved_by = actor_id;

-- ---- orders: customer cancellation request ----
ALTER TABLE orders ADD COLUMN cancel_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN cancel_request_reason VARCHAR(500);
ALTER TABLE orders ADD COLUMN cancel_requested_at TIMESTAMPTZ;

-- ---- DATA FIX ----
-- Old behaviour marked LIVE payments REFUNDED the moment the order was cancelled, but no money
-- moved. Any REFUNDED LIVE payment that never got a real bank reference is really waiting.
CREATE TEMP TABLE _fake_refunded AS
SELECT p.id AS payment_id, p.order_id
  FROM payments p
 WHERE p.status = 'REFUNDED'
   AND p.paid_mode = 'LIVE'
   AND NOT EXISTS (SELECT 1 FROM refunds r
                    WHERE r.payment_id = p.id AND r.bank_reference IS NOT NULL);

-- existing (fake-completed) refund rows become PENDING
UPDATE refunds r
   SET status = 'PENDING',
       payer_account = (SELECT p.from_account_id FROM payments p WHERE p.id = r.payment_id),
       note = 'Migrated: previously marked refunded without a bank transfer'
 WHERE r.payment_id IN (SELECT payment_id FROM _fake_refunded);

-- payments with no refund row at all get one
INSERT INTO refunds (payment_id, amount_cents, payer_account, reason, status, requested_at,
                     created_at, note)
SELECT p.id, p.amount_cents, p.from_account_id, 'Order cancelled', 'PENDING', now(), now(),
       'Migrated: previously marked refunded without a bank transfer'
  FROM payments p
 WHERE p.id IN (SELECT payment_id FROM _fake_refunded)
   AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.payment_id = p.id);

UPDATE payments SET status = 'REFUND_PENDING'
 WHERE id IN (SELECT payment_id FROM _fake_refunded);
UPDATE orders SET payment_status = 'REFUND_PENDING'
 WHERE id IN (SELECT order_id FROM _fake_refunded) AND payment_status = 'REFUNDED';

INSERT INTO order_status_history (order_id, from_status, to_status, note, created_at)
SELECT f.order_id, 'CANCELLED', 'CANCELLED',
       'Refund pending: $' || to_char(p.amount_cents / 100.0, 'FM999999990.00')
         || COALESCE(' to ' || p.from_account_id, '') || ' (manual bank transfer)',
       now()
  FROM _fake_refunded f JOIN payments p ON p.id = f.payment_id;

DROP TABLE _fake_refunded;
