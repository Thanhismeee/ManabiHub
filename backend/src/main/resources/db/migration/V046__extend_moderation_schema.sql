-- Moderation schema extension (V046).
-- 1. Update violation_reports status
ALTER TABLE violation_reports DROP CONSTRAINT chk_violation_reports_status;

-- Migrate existing statuses to new statuses
UPDATE violation_reports SET status = 'PENDING_REVIEW' WHERE status = 'PENDING';
UPDATE violation_reports SET status = 'RESOLVED_UPHELD' WHERE status = 'RESOLVED';
UPDATE violation_reports SET status = 'RESOLVED_NO_VIOLATION' WHERE status = 'REJECTED';

ALTER TABLE violation_reports ADD CONSTRAINT chk_violation_reports_status
CHECK (status IN ('PENDING_REVIEW', 'IN_REVIEW', 'PENDING_EVIDENCE', 'CORRECTION_REQUIRED', 'RESOLVED_UPHELD', 'RESOLVED_NO_VIOLATION', 'INVALID', 'CANCELLED'));

ALTER TABLE violation_reports ALTER COLUMN status SET DEFAULT 'PENDING_REVIEW';

-- 2. Update moderation_decisions
ALTER TABLE moderation_decisions RENAME COLUMN decision TO decision_type;
ALTER TABLE moderation_decisions DROP CONSTRAINT chk_moderation_decision;

-- Create action records before normalizing legacy decision values so their
-- enforcement intent is not lost.
CREATE TABLE moderation_action_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    moderation_decision_id UUID NOT NULL REFERENCES moderation_decisions (id),
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_moderation_action_type CHECK (action_type IN ('NONE', 'FORCE_DRAFT', 'REMOVE_CONTENT', 'HIDE_COURSE', 'BAN_ACCOUNT', 'FREEZE_BALANCE'))
);

INSERT INTO moderation_action_records (
    moderation_decision_id,
    action_type,
    target_type,
    target_id
)
SELECT
    decision.id,
    CASE decision.decision_type
        WHEN 'BAN' THEN 'BAN_ACCOUNT'
        ELSE decision.decision_type
    END,
    report.target_type,
    report.target_id
FROM moderation_decisions decision
JOIN violation_reports report
  ON report.id = decision.violation_report_id
WHERE decision.decision_type IN ('FORCE_DRAFT', 'REMOVE_CONTENT', 'BAN');

CREATE INDEX idx_moderation_action_records_decision
    ON moderation_action_records(moderation_decision_id);

-- Normalize legacy decisions after preserving their action.
UPDATE moderation_decisions SET decision_type = 'UPHELD' WHERE decision_type IN ('FORCE_DRAFT', 'REMOVE_CONTENT', 'BAN');
UPDATE moderation_decisions SET decision_type = 'DISMISSED' WHERE decision_type IN ('NO_VIOLATION', 'DISMISS');
UPDATE moderation_decisions SET decision_type = 'CORRECTION_REQUIRED' WHERE decision_type = 'REQUEST_CORRECTION';

ALTER TABLE moderation_decisions ADD COLUMN status_before VARCHAR(30);
ALTER TABLE moderation_decisions ADD COLUMN status_after VARCHAR(30);

ALTER TABLE moderation_decisions ADD CONSTRAINT chk_moderation_decision_type
CHECK (decision_type IN ('UPHELD', 'DISMISSED', 'PENDING_EVIDENCE', 'CORRECTION_REQUIRED'));
