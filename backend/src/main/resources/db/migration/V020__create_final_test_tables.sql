CREATE TABLE final_tests (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL UNIQUE,
    time_limit_minutes INT NOT NULL,
    passing_score INT NOT NULL,
    max_retakes INT NOT NULL,
    jlpt_level VARCHAR(10) NOT NULL,
    skill_focus VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_final_test_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
);

CREATE TABLE final_test_questions (
    id UUID PRIMARY KEY,
    final_test_id UUID NOT NULL,
    content TEXT NOT NULL,
    explanation TEXT NOT NULL,
    order_index INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_question_final_test FOREIGN KEY (final_test_id) REFERENCES final_tests (id) ON DELETE CASCADE
);

CREATE TABLE final_test_choices (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL,
    content TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_choice_question FOREIGN KEY (question_id) REFERENCES final_test_questions (id) ON DELETE CASCADE
);
