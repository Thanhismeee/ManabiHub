ALTER TABLE course_reviews
    ADD COLUMN teacher_reply_text VARCHAR(2000),
    ADD COLUMN teacher_replied_at TIMESTAMPTZ;

ALTER TABLE course_reviews
    ADD CONSTRAINT chk_course_reviews_teacher_reply
        CHECK (
            (teacher_reply_text IS NULL AND teacher_replied_at IS NULL)
            OR (
                CHAR_LENGTH(BTRIM(teacher_reply_text)) BETWEEN 2 AND 2000
                AND teacher_replied_at IS NOT NULL
            )
        );
