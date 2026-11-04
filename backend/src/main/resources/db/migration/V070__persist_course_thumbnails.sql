CREATE TABLE course_thumbnail_assets (
    id UUID PRIMARY KEY,
    file_name VARCHAR(128) NOT NULL UNIQUE,
    content_type VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_course_thumbnail_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT chk_course_thumbnail_size
        CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);

CREATE INDEX idx_course_thumbnail_assets_created_at
    ON course_thumbnail_assets (created_at);
