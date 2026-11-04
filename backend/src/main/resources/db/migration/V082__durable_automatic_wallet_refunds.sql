ALTER TABLE refund_requests
    ADD COLUMN auto_refund_next_attempt_at TIMESTAMPTZ,
    ADD COLUMN auto_refund_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN auto_refund_last_error_code VARCHAR(80);

CREATE INDEX idx_refund_automatic_due ON refund_requests (auto_refund_next_attempt_at, id)
    WHERE status = 'PENDING' AND auto_refund_next_attempt_at IS NOT NULL;

-- Recover only untouched, eligible requests abandoned by the old afterCommit path.
-- Finance decisions and reconciliation cases must retain their existing workflow.
UPDATE refund_requests
SET auto_refund_next_attempt_at = CURRENT_TIMESTAMP
WHERE status = 'PENDING' AND decided_at IS NULL AND decided_by IS NULL
  AND provider_status = 'NOT_REQUESTED'
  AND eligibility_snapshot->>'refundType' = 'STANDARD'
  AND eligibility_snapshot->>'eligibilityResult' = 'STANDARD_ELIGIBLE'
  AND eligibility_snapshot->>'eligible' = 'true';

-- An absent historical reason cannot be reconstructed safely.
UPDATE refund_requests
SET reconciliation_reason_code = 'LEGACY_REASON_NOT_RECORDED'
WHERE status = 'RECONCILIATION_REQUIRED'
  AND NULLIF(BTRIM(reconciliation_reason_code), '') IS NULL;

ALTER TABLE refund_requests ADD CONSTRAINT chk_refund_reconciliation_reason
    CHECK (status <> 'RECONCILIATION_REQUIRED'
        OR NULLIF(BTRIM(reconciliation_reason_code), '') IS NOT NULL);
