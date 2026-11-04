-- A browser integrity violation consumes the active Final Test attempt.
-- Keep this additive so existing attempt history remains immutable.

ALTER TABLE final_test_attempts
    ALTER COLUMN status TYPE VARCHAR(32);

ALTER TABLE final_test_attempts
    DROP CONSTRAINT chk_final_test_attempt_status;

ALTER TABLE final_test_attempts
    ADD CONSTRAINT chk_final_test_attempt_status
        CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'TIMED_OUT', 'TERMINATED_FOR_VIOLATION'));
