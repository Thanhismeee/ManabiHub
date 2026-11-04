-- Harden withdrawal creation for multi-instance deployments and protect bank details at rest.

CREATE TABLE withdrawal_otp_challenges (
    user_id UUID PRIMARY KEY REFERENCES app_users (id) ON DELETE CASCADE,
    code_hash VARCHAR(128) NOT NULL,
    nonce VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    resend_available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE teacher_bank_accounts
    ALTER COLUMN account_number TYPE VARCHAR(512),
    ADD COLUMN account_fingerprint VARCHAR(64);

CREATE UNIQUE INDEX uq_teacher_bank_account_fingerprint
    ON teacher_bank_accounts (teacher_id, account_fingerprint)
    WHERE account_fingerprint IS NOT NULL;

CREATE UNIQUE INDEX uq_withdrawal_request_pending_teacher
    ON withdrawal_requests (teacher_id)
    WHERE status = 'PENDING';
