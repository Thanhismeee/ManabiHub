-- ============================================================================
-- V016__add_course_metadata_goals.sql
-- MHB-15 / UC-23: Course draft metadata and learning goals.
-- ============================================================================

ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS introduction TEXT,
    ADD COLUMN IF NOT EXISTS category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS thumbnail_url TEXT,
    ADD COLUMN IF NOT EXISTS outcomes TEXT,
    ADD COLUMN IF NOT EXISTS prerequisites TEXT,
    ADD COLUMN IF NOT EXISTS target_students TEXT;

CREATE TABLE IF NOT EXISTS course_learning_goals (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id   UUID         NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    goal_text   VARCHAR(160) NOT NULL,
    order_index INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT uq_course_learning_goals_order UNIQUE (course_id, order_index)
);

CREATE INDEX IF NOT EXISTS idx_course_learning_goals_course_id
    ON course_learning_goals (course_id);
