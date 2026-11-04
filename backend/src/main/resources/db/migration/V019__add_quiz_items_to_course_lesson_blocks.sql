-- ============================================================================
-- V019__add_quiz_items_to_course_lesson_blocks.sql
-- MHB-16 / UC-34: Allow one quiz block to contain multiple questions.
-- ============================================================================

ALTER TABLE course_lesson_blocks
    ADD COLUMN IF NOT EXISTS quiz_items_json TEXT;
