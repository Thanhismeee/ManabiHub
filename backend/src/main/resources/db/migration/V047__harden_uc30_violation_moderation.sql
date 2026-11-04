-- UC-30 hardening (V047): permission separation, evidence, immutable action snapshots,
-- and soft removal metadata. This migration deliberately preserves all
-- historical reports, decisions, enrollments, purchases and course content.

ALTER TABLE violation_reports
    ADD COLUMN description TEXT;

ALTER TABLE moderation_decisions
    ADD COLUMN correlation_id UUID,
    ADD COLUMN evidence_requested_from VARCHAR(20),
    ADD CONSTRAINT chk_moderation_evidence_requested_from
        CHECK (
            evidence_requested_from IS NULL
            OR evidence_requested_from IN ('REPORTER', 'CREATOR', 'BOTH')
        );

ALTER TABLE moderation_action_records
    ADD COLUMN before_value JSONB,
    ADD COLUMN after_value JSONB;

ALTER TABLE course_lesson_blocks
    ADD COLUMN moderation_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN moderation_hidden_at TIMESTAMPTZ;

CREATE TABLE violation_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    violation_report_id UUID NOT NULL REFERENCES violation_reports (id),
    evidence_type VARCHAR(30) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    external_url TEXT NOT NULL,
    content_type VARCHAR(100),
    submitted_by UUID REFERENCES app_users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_violation_evidence_type
        CHECK (evidence_type IN ('LINK', 'IMAGE', 'DOCUMENT', 'VIDEO'))
);

CREATE INDEX idx_violation_evidence_report
    ON violation_evidence (violation_report_id, created_at);

INSERT INTO permissions (id, code, name, description)
VALUES
    ('b0000000-0000-0000-0000-00000000000c',
     'VIOLATION_CONTENT_ENFORCE',
     'Apply Content Moderation Actions',
     'Force draft, hide courses, or soft-remove reported content'),
    ('b0000000-0000-0000-0000-00000000000d',
     'VIOLATION_SEVERE_ENFORCE',
     'Apply Severe Moderation Actions',
     'Ban an account or freeze a teacher wallet after an upheld violation')
ON CONFLICT (code) DO NOTHING;

-- Course Managers own the standard UC-30 workflow and content enforcement.
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
  ON permission.code IN ('VIOLATION_RESOLVE', 'VIOLATION_CONTENT_ENFORCE')
WHERE role.code = 'COURSE_MANAGER'
ON CONFLICT DO NOTHING;

-- System Admin is an explicit, permission-mapped escalation actor. It can
-- perform both content and severe account/financial restrictions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
  ON permission.code IN (
      'VIOLATION_RESOLVE',
      'VIOLATION_CONTENT_ENFORCE',
      'VIOLATION_SEVERE_ENFORCE'
  )
WHERE role.code = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
