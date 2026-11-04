-- ============================================================================
-- V018__add_course_builder_lesson_blocks.sql
-- MHB-16 / UC-34: Configure lesson blocks for modular course builder.
-- ============================================================================

CREATE TABLE IF NOT EXISTS course_modules (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id   UUID         NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    title       VARCHAR(120) NOT NULL,
    description TEXT,
    order_index INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT uq_course_modules_order UNIQUE (course_id, order_index)
);

CREATE INDEX IF NOT EXISTS idx_course_modules_course_id
    ON course_modules (course_id);

CREATE TABLE IF NOT EXISTS course_lesson_blocks (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id         UUID         NOT NULL REFERENCES course_modules (id) ON DELETE CASCADE,
    block_type        VARCHAR(30)  NOT NULL,
    title             VARCHAR(160) NOT NULL,
    content           TEXT,
    video_url         TEXT,
    duration_minutes  INT,
    quiz_question     TEXT,
    quiz_options_json TEXT,
    quiz_answer       TEXT,
    flashcards_json   TEXT,
    writing_prompt    TEXT,
    rubric            TEXT,
    order_index       INT          NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ,

    CONSTRAINT ck_course_lesson_blocks_type CHECK (block_type IN ('VIDEO', 'TEXT', 'QUIZ', 'FLASHCARD', 'WRITING')),
    CONSTRAINT ck_course_lesson_blocks_duration CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    CONSTRAINT uq_course_lesson_blocks_order UNIQUE (module_id, order_index)
);

CREATE INDEX IF NOT EXISTS idx_course_lesson_blocks_module_id
    ON course_lesson_blocks (module_id);

CREATE INDEX IF NOT EXISTS idx_course_lesson_blocks_type
    ON course_lesson_blocks (block_type);
