-- MHB-33: support writing submissions created from the current course builder.
-- Legacy seed data references lessons; new course content uses course_lesson_blocks.

ALTER TABLE writing_submissions
    ADD COLUMN IF NOT EXISTS lesson_block_id UUID;

ALTER TABLE writing_submissions
    ALTER COLUMN lesson_id DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_writing_submissions_lesson_block'
    ) THEN
        ALTER TABLE writing_submissions
            ADD CONSTRAINT fk_writing_submissions_lesson_block
            FOREIGN KEY (lesson_block_id) REFERENCES course_lesson_blocks (id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_writing_submissions_content_reference'
    ) THEN
        ALTER TABLE writing_submissions
            ADD CONSTRAINT chk_writing_submissions_content_reference
            CHECK (
                (lesson_id IS NOT NULL AND lesson_block_id IS NULL)
                OR (lesson_id IS NULL AND lesson_block_id IS NOT NULL)
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_writing_submissions_lesson_block_id
    ON writing_submissions (lesson_block_id);
