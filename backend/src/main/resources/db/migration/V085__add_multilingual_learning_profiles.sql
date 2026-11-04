-- Add language-aware course and assessment metadata without changing legacy IDs.
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS learning_language VARCHAR(32),
    ADD COLUMN IF NOT EXISTS proficiency_framework VARCHAR(32),
    ADD COLUMN IF NOT EXISTS framework_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS proficiency_level VARCHAR(32);

UPDATE courses
SET learning_language = COALESCE(learning_language, 'JAPANESE'),
    proficiency_framework = COALESCE(proficiency_framework, 'JLPT'),
    framework_version = COALESCE(framework_version, 'CURRENT'),
    proficiency_level = COALESCE(proficiency_level, level_code)
WHERE learning_language IS NULL
   OR proficiency_framework IS NULL
   OR framework_version IS NULL
   OR proficiency_level IS NULL;

ALTER TABLE courses
    ALTER COLUMN learning_language SET DEFAULT 'JAPANESE',
    ALTER COLUMN learning_language SET NOT NULL,
    ALTER COLUMN proficiency_framework SET DEFAULT 'JLPT',
    ALTER COLUMN proficiency_framework SET NOT NULL,
    ALTER COLUMN framework_version SET DEFAULT 'CURRENT',
    ALTER COLUMN framework_version SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_courses_language_framework_level
    ON courses (learning_language, proficiency_framework, proficiency_level);

ALTER TABLE final_tests
    ADD COLUMN IF NOT EXISTS learning_language VARCHAR(32),
    ADD COLUMN IF NOT EXISTS proficiency_framework VARCHAR(32),
    ADD COLUMN IF NOT EXISTS framework_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS proficiency_level VARCHAR(32),
    ADD COLUMN IF NOT EXISTS assessment_format VARCHAR(32);

UPDATE final_tests final_test
SET learning_language = COALESCE(final_test.learning_language, course.learning_language, 'JAPANESE'),
    proficiency_framework = COALESCE(final_test.proficiency_framework, course.proficiency_framework, 'JLPT'),
    framework_version = COALESCE(final_test.framework_version, course.framework_version, 'CURRENT'),
    proficiency_level = COALESCE(final_test.proficiency_level, final_test.jlpt_level, course.proficiency_level),
    assessment_format = COALESCE(final_test.assessment_format, 'COURSE')
FROM courses course
WHERE course.id = final_test.course_id;

ALTER TABLE final_tests
    ALTER COLUMN learning_language SET DEFAULT 'JAPANESE',
    ALTER COLUMN learning_language SET NOT NULL,
    ALTER COLUMN proficiency_framework SET DEFAULT 'JLPT',
    ALTER COLUMN proficiency_framework SET NOT NULL,
    ALTER COLUMN framework_version SET DEFAULT 'CURRENT',
    ALTER COLUMN framework_version SET NOT NULL,
    ALTER COLUMN assessment_format SET DEFAULT 'COURSE',
    ALTER COLUMN assessment_format SET NOT NULL,
    ALTER COLUMN jlpt_level DROP NOT NULL;

CREATE TABLE IF NOT EXISTS student_learning_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_profile_id UUID NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    learning_language VARCHAR(32) NOT NULL,
    proficiency_framework VARCHAR(32) NOT NULL,
    framework_version VARCHAR(32) NOT NULL DEFAULT 'CURRENT',
    proficiency_level VARCHAR(32) NOT NULL,
    primary_goal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_student_learning_goal UNIQUE (
        student_profile_id,
        learning_language,
        proficiency_framework,
        framework_version,
        proficiency_level
    )
);

INSERT INTO student_learning_goals (
    id, student_profile_id, learning_language, proficiency_framework,
    framework_version, proficiency_level, primary_goal
)
SELECT gen_random_uuid(), profile.id, 'JAPANESE', 'JLPT', 'CURRENT', UPPER(profile.jlpt_goal), TRUE
FROM student_profiles profile
WHERE profile.jlpt_goal IS NOT NULL
  AND BTRIM(profile.jlpt_goal) <> ''
  AND UPPER(profile.jlpt_goal) IN ('N1', 'N2', 'N3', 'N4', 'N5')
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_student_learning_goals_profile
    ON student_learning_goals (student_profile_id);
