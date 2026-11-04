-- MHB-68: enrollment-bound course ratings and reviews.
--
-- V038/V039 are already owned by develop. V040 secures withdrawals before
-- this review migration so production Flyway history remains linear.

CREATE TABLE course_reviews (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id  UUID         NOT NULL REFERENCES enrollments (id) ON DELETE CASCADE,
    rating         INTEGER      NOT NULL,
    review_text    VARCHAR(2000) NOT NULL,
    review_status  VARCHAR(20)  NOT NULL DEFAULT 'APPROVED',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_course_reviews_enrollment UNIQUE (enrollment_id),
    CONSTRAINT chk_course_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_course_reviews_text_length
        CHECK (CHAR_LENGTH(BTRIM(review_text)) BETWEEN 10 AND 2000),
    CONSTRAINT chk_course_reviews_status
        CHECK (review_status IN ('PENDING', 'APPROVED', 'HIDDEN'))
);

CREATE INDEX idx_course_reviews_status_updated
    ON course_reviews (review_status, updated_at DESC);

CREATE INDEX idx_course_reviews_enrollment
    ON course_reviews (enrollment_id);
