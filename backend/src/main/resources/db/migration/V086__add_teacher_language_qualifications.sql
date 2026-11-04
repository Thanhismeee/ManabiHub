CREATE TABLE IF NOT EXISTS teacher_language_qualifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES teacher_profiles(id) ON DELETE CASCADE,
    learning_language VARCHAR(32) NOT NULL,
    proficiency_framework VARCHAR(32) NOT NULL,
    framework_version VARCHAR(32) NOT NULL DEFAULT 'CURRENT',
    credential_level VARCHAR(64) NOT NULL,
    credential_type VARCHAR(40) NOT NULL,
    credential_code VARCHAR(120),
    issuer VARCHAR(255) NOT NULL,
    evidence_path TEXT NOT NULL,
    evidence_file_name VARCHAR(255) NOT NULL,
    evidence_mime_type VARCHAR(100) NOT NULL,
    evidence_file_size BIGINT NOT NULL,
    evidence_sha256 VARCHAR(64) NOT NULL,
    copyright_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_note TEXT,
    reviewed_by UUID REFERENCES internal_admin_accounts(id),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT chk_teacher_language_qualification_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_teacher_language_credential_type
        CHECK (credential_type IN ('OFFICIAL_CERTIFICATE', 'UNIVERSITY_DEGREE', 'SOFL_INTERNAL_APPROVAL', 'EMPLOYMENT_RECORD'))
);

CREATE INDEX IF NOT EXISTS idx_teacher_language_qualification_teacher
    ON teacher_language_qualifications (teacher_id, status);

CREATE INDEX IF NOT EXISTS idx_teacher_language_qualification_review_queue
    ON teacher_language_qualifications (status, submitted_at);

CREATE INDEX IF NOT EXISTS idx_teacher_language_qualification_scope
    ON teacher_language_qualifications (
        teacher_id, learning_language, proficiency_framework, status
    );

-- Preserve the publication rights of teachers approved by the legacy JLPT flow.
INSERT INTO teacher_language_qualifications (
    id, teacher_id, learning_language, proficiency_framework, framework_version,
    credential_level, credential_type, issuer, evidence_path, evidence_file_name,
    evidence_mime_type, evidence_file_size, evidence_sha256, copyright_agreed, status,
    review_note, submitted_at, reviewed_at
)
SELECT gen_random_uuid(), profile.id, 'JAPANESE', 'JLPT', 'CURRENT',
       'LEGACY_VERIFIED', 'OFFICIAL_CERTIFICATE', 'Legacy ManabiHub KYC',
       'legacy://kyc/' || profile.id, 'legacy-verification',
       'application/octet-stream', 0, repeat('0', 64), TRUE, 'APPROVED',
       'Migrated from the approved JLPT verification flow.',
       COALESCE(profile.updated_at, profile.created_at, NOW()),
       COALESCE(profile.updated_at, NOW())
FROM teacher_profiles profile
WHERE profile.kyc_status = 'APPROVED'
  AND profile.can_publish_course = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM teacher_language_qualifications qualification
      WHERE qualification.teacher_id = profile.id
        AND qualification.learning_language = 'JAPANESE'
        AND qualification.proficiency_framework = 'JLPT'
        AND qualification.status = 'APPROVED'
  );
