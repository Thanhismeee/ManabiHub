-- V029__add_lesson_block_violation_target.sql

-- Drop the old constraint to allow the new target type
ALTER TABLE violation_reports DROP CONSTRAINT chk_violation_reports_target_type;

-- Add the updated constraint including LESSON_BLOCK
ALTER TABLE violation_reports ADD CONSTRAINT chk_violation_reports_target_type CHECK (target_type IN ('COURSE', 'LESSON', 'LESSON_BLOCK', 'REVIEW', 'USER'));
