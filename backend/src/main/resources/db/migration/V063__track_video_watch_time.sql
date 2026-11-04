ALTER TABLE lesson_block_progress
    ADD COLUMN watched_video_seconds INTEGER NOT NULL DEFAULT 0;

ALTER TABLE lesson_block_progress
    ADD CONSTRAINT chk_lesson_block_progress_watched_video_seconds
        CHECK (watched_video_seconds >= 0);
