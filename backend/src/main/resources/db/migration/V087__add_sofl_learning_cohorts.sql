CREATE TABLE IF NOT EXISTS learning_cohorts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL REFERENCES courses(id),
    owner_teacher_id UUID NOT NULL REFERENCES teacher_profiles(id),
    name VARCHAR(160) NOT NULL,
    code VARCHAR(32) NOT NULL UNIQUE,
    organization_name VARCHAR(160) NOT NULL DEFAULT 'SOFL',
    branch_name VARCHAR(160),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    max_students INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_learning_cohort_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_learning_cohort_capacity CHECK (max_students BETWEEN 1 AND 500),
    CONSTRAINT chk_learning_cohort_status
        CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_learning_cohorts_teacher
    ON learning_cohorts (owner_teacher_id, status, start_date);
CREATE INDEX IF NOT EXISTS idx_learning_cohorts_course
    ON learning_cohorts (course_id);

CREATE TABLE IF NOT EXISTS cohort_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id UUID NOT NULL REFERENCES learning_cohorts(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student_profiles(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    removed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_cohort_member UNIQUE (cohort_id, student_id),
    CONSTRAINT chk_cohort_member_status CHECK (status IN ('ACTIVE', 'REMOVED', 'COMPLETED'))
);

CREATE INDEX IF NOT EXISTS idx_cohort_members_student
    ON cohort_members (student_id, status);

ALTER TABLE enrollments
    ADD COLUMN IF NOT EXISTS access_source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS source_cohort_id UUID REFERENCES learning_cohorts(id);

UPDATE enrollments
SET access_source = 'PURCHASE'
WHERE access_source IS NULL;

ALTER TABLE enrollments
    ALTER COLUMN access_source SET DEFAULT 'PURCHASE',
    ALTER COLUMN access_source SET NOT NULL;

ALTER TABLE enrollments
    DROP CONSTRAINT IF EXISTS chk_enrollments_access_source;
ALTER TABLE enrollments
    ADD CONSTRAINT chk_enrollments_access_source
        CHECK (access_source IN ('PURCHASE', 'SOFL_COHORT', 'ADMIN_GRANT'));

CREATE INDEX IF NOT EXISTS idx_enrollments_source_cohort
    ON enrollments (source_cohort_id)
    WHERE source_cohort_id IS NOT NULL;
