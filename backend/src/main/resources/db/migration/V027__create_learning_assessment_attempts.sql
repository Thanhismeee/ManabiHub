-- MHB-27 / UC-12: canonical quiz and final-test attempt persistence.

CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments (id) ON DELETE CASCADE,
    lesson_block_id UUID NOT NULL REFERENCES course_lesson_blocks (id) ON DELETE CASCADE,
    score NUMERIC(5, 2) NOT NULL,
    passed BOOLEAN NOT NULL,
    answers_json JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_quiz_attempts_enrollment_block
    ON quiz_attempts (enrollment_id, lesson_block_id, created_at DESC);

CREATE TABLE final_test_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments (id) ON DELETE CASCADE,
    final_test_id UUID NOT NULL REFERENCES final_tests (id) ON DELETE CASCADE,
    score NUMERIC(5, 2),
    passed BOOLEAN,
    answers_json JSONB,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_final_test_attempt_status
        CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'TIMED_OUT'))
);

CREATE INDEX idx_final_test_attempts_enrollment_test
    ON final_test_attempts (enrollment_id, final_test_id, started_at DESC);
