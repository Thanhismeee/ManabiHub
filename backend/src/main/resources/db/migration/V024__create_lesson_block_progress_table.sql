-- ============================================================================
-- V024__create_lesson_block_progress_table.sql
-- MHB-25 / UC-10: Study Course Lessons.
-- Creates a separate lesson_block_progress table for the new course builder
-- without altering or dropping the legacy lesson_progress table.
-- ============================================================================

CREATE TABLE lesson_block_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments (id) ON DELETE CASCADE,
    lesson_block_id UUID NOT NULL REFERENCES course_lesson_blocks (id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    last_video_position_seconds INT,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_lesson_block_progress_enrollment_block UNIQUE (enrollment_id, lesson_block_id),
    CONSTRAINT chk_lesson_block_progress_video_position CHECK (last_video_position_seconds IS NULL OR last_video_position_seconds >= 0)
);

CREATE INDEX idx_lesson_block_progress_enrollment_id ON lesson_block_progress (enrollment_id);
CREATE INDEX idx_lesson_block_progress_lesson_block_id ON lesson_block_progress (lesson_block_id);
