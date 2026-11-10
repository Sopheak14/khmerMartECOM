-- Email verification by 6-digit OTP (replaces the link-token flow).
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Every account that exists before this migration keeps working.
UPDATE users
   SET email_verified = TRUE,
       email_verified_at = COALESCE(email_verified_at, now());

-- One current code per user; re-sending overwrites it (old code invalid, attempts reset).
CREATE TABLE email_otp (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_sent_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

-- The link-token table is no longer used.
DROP TABLE email_verification_tokens;
