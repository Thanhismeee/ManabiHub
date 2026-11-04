-- MHB-24 / UC-07: owner-scoped student course wishlist.

CREATE TABLE student_wishlist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES student_profiles (id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_wishlist_student_course UNIQUE (student_id, course_id)
);

CREATE INDEX idx_student_wishlist_student_created
    ON student_wishlist (student_id, created_at DESC);
