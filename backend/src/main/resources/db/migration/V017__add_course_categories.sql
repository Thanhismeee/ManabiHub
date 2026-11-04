-- ============================================================================
-- V017__add_course_categories.sql
-- MHB-15 / UC-23: Standard course categories for course draft creation.
-- ============================================================================

CREATE TABLE IF NOT EXISTS course_categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(100) UNIQUE NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_course_categories_active_sort
    ON course_categories (active, sort_order, name);

INSERT INTO course_categories (code, name, description, active, sort_order)
VALUES
    ('VOCABULARY', 'Từ vựng', 'Khóa học tập trung vào vốn từ, cụm từ và ngữ cảnh sử dụng.', TRUE, 10),
    ('GRAMMAR', 'Ngữ pháp', 'Khóa học luyện cấu trúc câu, mẫu ngữ pháp và cách áp dụng.', TRUE, 20),
    ('JLPT_PRACTICE', 'Luyện đề JLPT', 'Khóa học luyện đề, chiến thuật làm bài và tổng ôn JLPT.', TRUE, 30),
    ('LISTENING', 'Nghe hiểu', 'Khóa học rèn nghe hiểu tiếng Nhật theo tình huống hoặc cấp độ.', TRUE, 40),
    ('SPEAKING', 'Giao tiếp', 'Khóa học luyện hội thoại, phản xạ và phát âm.', TRUE, 50),
    ('KANJI', 'Kanji', 'Khóa học học chữ Hán, âm đọc, nghĩa và cách ghi nhớ.', TRUE, 60),
    ('WRITING', 'Viết Sakubun', 'Khóa học luyện viết câu, đoạn văn và bài luận tiếng Nhật.', TRUE, 70)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    active = EXCLUDED.active,
    sort_order = EXCLUDED.sort_order;
