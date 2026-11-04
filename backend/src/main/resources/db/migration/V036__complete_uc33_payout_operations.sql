-- UC-33 operational hardening:
-- real wallet freeze state, durable reconciliation evidence, and manual transfer proof.

ALTER TABLE wallets
    ADD COLUMN frozen BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE payout_settlements
    ADD COLUMN transfer_method VARCHAR(20) NOT NULL DEFAULT 'GATEWAY',
    ADD COLUMN manual_proof_storage_key VARCHAR(500),
    ADD COLUMN manual_proof_original_name VARCHAR(255),
    ADD COLUMN manual_proof_content_type VARCHAR(100),
    ADD COLUMN manual_proof_size BIGINT,
    ADD COLUMN manual_transferred_at TIMESTAMPTZ,
    ADD COLUMN notification_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN notification_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE payout_settlements
    ADD CONSTRAINT chk_payout_transfer_method
        CHECK (transfer_method IN ('GATEWAY', 'MANUAL')),
    ADD CONSTRAINT chk_payout_manual_proof_size
        CHECK (manual_proof_size IS NULL OR manual_proof_size > 0),
    ADD CONSTRAINT chk_payout_notification_status
        CHECK (notification_status IN ('NOT_REQUIRED', 'PENDING', 'SENT', 'FAILED')),
    ADD CONSTRAINT chk_payout_notification_attempts
        CHECK (notification_attempts >= 0);

CREATE TABLE payout_reconciliation_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    withdrawal_request_id   UUID NOT NULL REFERENCES withdrawal_requests (id),
    payout_settlement_id    UUID REFERENCES payout_settlements (id),
    checked_by              UUID NOT NULL REFERENCES internal_admin_accounts (id),
    trigger_type            VARCHAR(30) NOT NULL,
    status                  VARCHAR(30) NOT NULL,
    alerts                  JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_snapshot       JSONB NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_payout_reconciliation_log_trigger
        CHECK (trigger_type IN ('DETAIL_REVIEW', 'APPROVAL', 'FINALIZATION', 'MANUAL_TRANSFER')),
    CONSTRAINT chk_payout_reconciliation_log_status
        CHECK (status IN ('MATCHED', 'WARNING', 'CRITICAL_MISMATCH', 'RESOLVED'))
);

CREATE INDEX idx_payout_reconciliation_logs_withdrawal
    ON payout_reconciliation_logs (withdrawal_request_id, created_at DESC);

CREATE INDEX idx_payout_reconciliation_logs_settlement
    ON payout_reconciliation_logs (payout_settlement_id);
