ALTER TABLE lesson_block_progress
    ADD COLUMN video_duration_seconds INTEGER;

ALTER TABLE lesson_block_progress
    ADD CONSTRAINT chk_lesson_block_progress_video_duration_seconds
        CHECK (video_duration_seconds IS NULL OR video_duration_seconds >= 1);
