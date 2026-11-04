ALTER TABLE cohort_submissions ADD COLUMN verified_audio_seconds INTEGER
    CHECK (verified_audio_seconds BETWEEN 1 AND 600);
ALTER TABLE ai_usage_logs DROP CONSTRAINT chk_ai_usage_request_status;
ALTER TABLE ai_usage_logs ADD CONSTRAINT chk_ai_usage_request_status CHECK(request_status IN ('SUCCESS','FAILED','BLOCKED','PROCESSING'));
CREATE INDEX idx_ai_usage_quota ON ai_usage_logs(user_id,created_at) WHERE request_status IN ('SUCCESS','PROCESSING','FAILED');
