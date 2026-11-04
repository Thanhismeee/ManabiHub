-- Demo student identity verification. Only an HMAC fingerprint and the
-- matched display fields are retained; the raw CCCD is never persisted.
ALTER TABLE student_profiles
    ADD COLUMN IF NOT EXISTS identity_fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS identity_provider VARCHAR(64),
    ADD COLUMN IF NOT EXISTS identity_full_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS identity_date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS identity_verified_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_student_profiles_identity_fingerprint
    ON student_profiles(identity_fingerprint)
    WHERE identity_fingerprint IS NOT NULL;

-- The VNPT sandbox demo account used by the student withdrawal walkthrough.
-- This is synthetic registry data, not a national-database lookup.
INSERT INTO mock_national_id_registry (
    id_number,
    full_name,
    date_of_birth,
    gender,
    permanent_address,
    issue_date,
    expiry_date,
    issue_place,
    document_status,
    front_back_match_status,
    corner_blur_status,
    id_quality_status,
    issue_date_status,
    expiry_status,
    document_identification_status,
    warning_status,
    overlay_image_status,
    open_eyes_status,
    blurred_face_status,
    face_validation_status,
    covered_face_status,
    face_matching_score,
    source_provider,
    source_reference,
    raw_payload,
    active
) VALUES (
    '027204002711',
    'NGUYEN XUAN DAT',
    DATE '2004-08-31',
    'Nam',
    'DEMO_RECORD',
    DATE '2021-04-25',
    DATE '2029-08-31',
    'DEMO_PROVIDER',
    'VALID',
    'IDENTICAL',
    'NO',
    'GOOD',
    'GOOD',
    'UNEXPIRED',
    'LIVE_SHOOTING',
    'VALID',
    'NO',
    'YES',
    'NO',
    'VALID',
    'NO',
    97.7800,
    'VNPT_EKYC_DEMO',
    'STUDENT_WITHDRAWAL_DEMO',
    '{}'::jsonb,
    TRUE
)
ON CONFLICT (id_number) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    date_of_birth = EXCLUDED.date_of_birth,
    active = EXCLUDED.active,
    updated_at = NOW();
