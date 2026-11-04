-- UC-33 extends the payout_settlements table created by V002.
-- Existing V002 rows are backfilled before the new NOT NULL constraints are added.

ALTER TABLE payout_settlements
    ADD COLUMN teacher_id UUID REFERENCES teacher_profiles (id),
    ADD COLUMN wallet_id UUID REFERENCES wallets (id),
    ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    ADD COLUMN idempotency_key VARCHAR(100),
    ADD COLUMN reconciliation_note VARCHAR(500),
    ADD COLUMN decision VARCHAR(50),
    ADD COLUMN decision_reason VARCHAR(500),
    ADD COLUMN processing_started_at TIMESTAMPTZ,
    ADD COLUMN failure_code VARCHAR(100),
    ADD COLUMN failure_message_sanitized VARCHAR(500),
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE payout_settlements settlement
SET teacher_id = request.teacher_id
FROM withdrawal_requests request
WHERE settlement.withdrawal_request_id = request.id
  AND settlement.teacher_id IS NULL;

UPDATE payout_settlements settlement
SET wallet_id = wallet.id
FROM wallets wallet
WHERE wallet.teacher_id = settlement.teacher_id
  AND wallet.owner_type = 'TEACHER'
  AND settlement.wallet_id IS NULL;

UPDATE payout_settlements
SET idempotency_key = 'payout-' || withdrawal_request_id::text
WHERE idempotency_key IS NULL;

UPDATE payout_settlements
SET status = CASE status
    WHEN 'PENDING' THEN 'PROCESSING'
    WHEN 'SUCCESS' THEN 'SUCCEEDED'
    WHEN 'RECONCILIATION_MISMATCH' THEN 'FAILED'
    ELSE status
END;

UPDATE payout_settlements
SET reconciliation_status = CASE reconciliation_status
    WHEN 'NOT_CHECKED' THEN 'WARNING'
    WHEN 'MISMATCHED' THEN 'CRITICAL_MISMATCH'
    ELSE COALESCE(reconciliation_status, 'WARNING')
END;

ALTER TABLE payout_settlements
    ALTER COLUMN teacher_id SET NOT NULL,
    ALTER COLUMN wallet_id SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL,
    ALTER COLUMN reconciliation_status SET NOT NULL,
    DROP CONSTRAINT IF EXISTS chk_payout_settlements_status,
    DROP CONSTRAINT IF EXISTS chk_payout_reconciliation,
    ADD CONSTRAINT chk_payout_settlements_status
        CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'PENDING_RETRY', 'REJECTED')),
    ADD CONSTRAINT chk_payout_reconciliation
        CHECK (reconciliation_status IN ('MATCHED', 'WARNING', 'CRITICAL_MISMATCH', 'RESOLVED')),
    ADD CONSTRAINT chk_payout_settlement_amount CHECK (amount > 0),
    ADD CONSTRAINT chk_payout_settlement_retry_count CHECK (retry_count >= 0),
    ADD CONSTRAINT uq_payout_settlement_withdrawal UNIQUE (withdrawal_request_id),
    ADD CONSTRAINT uq_payout_settlement_idempotency UNIQUE (idempotency_key);

CREATE UNIQUE INDEX uq_payout_settlement_provider_reference
    ON payout_settlements (provider, provider_reference_id)
    WHERE provider_reference_id IS NOT NULL;

CREATE UNIQUE INDEX uq_wallet_withdrawal_ledger
    ON wallet_transactions (reference_type, reference_id, transaction_type)
    WHERE reference_id IS NOT NULL
      AND transaction_type IN (
          'WITHDRAWAL_RESERVATION',
          'WITHDRAWAL_COMPLETED',
          'WITHDRAWAL_REJECTED',
          'WITHDRAWAL_CANCELLED'
      );

CREATE INDEX idx_payout_settlements_teacher_id ON payout_settlements (teacher_id);
CREATE INDEX idx_payout_settlements_wallet_id ON payout_settlements (wallet_id);
