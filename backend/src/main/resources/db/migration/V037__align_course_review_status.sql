-- The course approval workflow uses PENDING while the baseline constraint
-- still allowed the legacy SUBMITTED value.

UPDATE courses
SET status = 'PENDING'
WHERE status = 'SUBMITTED';

ALTER TABLE courses
    DROP CONSTRAINT IF EXISTS chk_courses_status,
    ADD CONSTRAINT chk_courses_status
        CHECK (status IN (
            'DRAFT',
            'PENDING',
            'APPROVED',
            'PUBLISHED',
            'REJECTED',
            'FORCED_DRAFT',
            'ARCHIVED'
        ));
