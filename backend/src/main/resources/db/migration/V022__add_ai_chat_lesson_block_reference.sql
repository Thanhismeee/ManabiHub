-- MHB-32 / UC-16: tie AI chat usage to the exact course lesson block.
-- Existing AI writing logs remain valid with a NULL lesson_block_id.

ALTER TABLE ai_usage_logs
    ADD COLUMN IF NOT EXISTS lesson_block_id UUID
        REFERENCES course_lesson_blocks (id);

CREATE INDEX IF NOT EXISTS idx_ai_usage_logs_lesson_block_id
    ON ai_usage_logs (lesson_block_id);
