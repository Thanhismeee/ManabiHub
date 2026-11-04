-- Add unique constraint for writing submissions per enrollment and lesson block
ALTER TABLE writing_submissions
    ADD CONSTRAINT uq_writing_submissions_enrollment_block UNIQUE (enrollment_id, lesson_block_id);
