-- Course access policy and the refund lock state machine.
-- Existing enrollments receive a deterministic 180-day deadline (or the course's
-- fixed deadline) without changing existing progress or financial records.

ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS access_duration_days INTEGER NOT NULL DEFAULT 180,
    ADD COLUMN IF NOT EXISTS access_expires_at TIMESTAMPTZ;

ALTER TABLE courses
    DROP CONSTRAINT IF EXISTS chk_courses_access_duration_days;
ALTER TABLE courses
    ADD CONSTRAINT chk_courses_access_duration_days
        CHECK (access_duration_days > 0);

ALTER TABLE enrollments
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

ALTER TABLE enrollments
    DROP CONSTRAINT IF EXISTS chk_enrollments_status;
ALTER TABLE enrollments
    ADD CONSTRAINT chk_enrollments_status CHECK (
        enrollment_status IN (
            'ACTIVE', 'REFUND_PENDING', 'REFUNDED', 'REVOKED', 'COMPLETED', 'EXPIRED'
        )
    );

UPDATE enrollments e
SET expires_at = CASE
    WHEN c.access_expires_at IS NOT NULL THEN c.access_expires_at
    ELSE e.enrolled_at + make_interval(days => c.access_duration_days)
END
FROM courses c
WHERE c.id = e.course_id
  AND e.expires_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_enrollments_expires_at ON enrollments (expires_at);
CREATE INDEX IF NOT EXISTS idx_enrollments_refund_pending
    ON enrollments (student_id, course_id)
    WHERE enrollment_status = 'REFUND_PENDING';
