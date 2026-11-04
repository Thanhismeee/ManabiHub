ALTER TABLE learning_cohorts
    ADD COLUMN feedback_quota_per_student INTEGER NOT NULL DEFAULT 12 CHECK (feedback_quota_per_student BETWEEN 1 AND 200),
    ADD COLUMN review_sla_hours INTEGER NOT NULL DEFAULT 48 CHECK (review_sla_hours BETWEEN 1 AND 168);
ALTER TABLE cohort_members ADD COLUMN last_reminder_at TIMESTAMPTZ;

CREATE TABLE cohort_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id UUID NOT NULL REFERENCES learning_cohorts(id),
    title VARCHAR(160) NOT NULL,
    instructions TEXT NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('WRITING', 'SPEAKING')),
    due_at TIMESTAMPTZ NOT NULL,
    accept_until TIMESTAMPTZ NOT NULL CHECK (accept_until >= due_at),
    max_attempts INTEGER NOT NULL CHECK (max_attempts BETWEEN 1 AND 10),
    max_audio_seconds INTEGER NOT NULL CHECK (max_audio_seconds BETWEEN 10 AND 600),
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cohort_assignments_due ON cohort_assignments(cohort_id, due_at);
CREATE TABLE cohort_submissions (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES cohort_assignments(id),
    member_id UUID NOT NULL REFERENCES cohort_members(id),
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    content TEXT,
    audio_path VARCHAR(1024),
    audio_mime_type VARCHAR(80),
    audio_bytes BIGINT,
    audio_sha256 VARCHAR(64),
    reported_audio_seconds INTEGER,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    review_due_at TIMESTAMPTZ NOT NULL,
    feedback TEXT,
    score INTEGER CHECK (score BETWEEN 0 AND 100),
    revision_requested BOOLEAN NOT NULL DEFAULT FALSE,
    review_minutes INTEGER CHECK (review_minutes BETWEEN 1 AND 180),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES app_users(id),
    CONSTRAINT uq_cohort_attempt UNIQUE (assignment_id, member_id, attempt),
    CONSTRAINT chk_cohort_submission_content CHECK (content IS NOT NULL OR audio_path IS NOT NULL)
);
CREATE INDEX idx_cohort_submissions_member ON cohort_submissions(member_id, submitted_at DESC);
CREATE INDEX idx_cohort_submissions_review ON cohort_submissions(review_due_at) WHERE reviewed_at IS NULL;
