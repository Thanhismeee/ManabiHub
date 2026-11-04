CREATE TABLE flashcard_progress (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    lesson_block_id UUID NOT NULL REFERENCES course_lesson_blocks(id) ON DELETE CASCADE,
    card_index INT NOT NULL CHECK (card_index >= 0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('REMEMBERED', 'NEEDS_REVIEW')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_flashcard_progress UNIQUE (enrollment_id, lesson_block_id, card_index)
);
