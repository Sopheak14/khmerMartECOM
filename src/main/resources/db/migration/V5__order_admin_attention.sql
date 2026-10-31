-- Flag orders that need a human look (e.g. a payment confirmed by Bakong after the QR expired).
ALTER TABLE orders ADD COLUMN needs_attention BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN attention_note VARCHAR(500);
