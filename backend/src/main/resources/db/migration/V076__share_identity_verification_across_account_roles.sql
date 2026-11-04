-- A successful CCCD verification belongs to the ManabiHub account, not to the
-- student or teacher entry point that happened to launch VNPT eKYC.
CREATE TABLE account_identity_verifications (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE RESTRICT,
    identity_fingerprint VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    full_name VARCHAR(255),
    date_of_birth DATE,
    verified_at TIMESTAMPTZ NOT NULL,
    source_subject VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_account_identity_verification_fingerprint UNIQUE (identity_fingerprint),
    CONSTRAINT chk_account_identity_verification_subject
        CHECK (source_subject IN ('STUDENT', 'TEACHER'))
);

-- Refuse to guess ownership if legacy role-specific stores disagree. This is
-- safer than silently assigning one CCCD or one account during the upgrade.
DO $$
BEGIN
    IF EXISTS (
        WITH latest_teacher AS (
            SELECT DISTINCT ON (teacher.user_id)
                   teacher.user_id,
                   claim.identity_fingerprint
            FROM teacher_identity_claims claim
            JOIN teacher_profiles teacher ON teacher.id = claim.teacher_id
            JOIN kyc_requests request ON request.teacher_id = teacher.id
            WHERE request.identity_status = 'VERIFIED'
              AND request.identity_verified_at IS NOT NULL
            ORDER BY teacher.user_id, request.identity_verified_at DESC, request.id
        ), legacy AS (
            SELECT student.user_id, student.identity_fingerprint
            FROM student_profiles student
            WHERE student.identity_verified_at IS NOT NULL
              AND student.identity_fingerprint IS NOT NULL
            UNION ALL
            SELECT user_id, identity_fingerprint FROM latest_teacher
        )
        SELECT 1
        FROM legacy
        GROUP BY identity_fingerprint
        HAVING COUNT(DISTINCT user_id) > 1
    ) THEN
        RAISE EXCEPTION 'Legacy CCCD fingerprint is owned by multiple accounts; manual review is required';
    END IF;

    IF EXISTS (
        WITH latest_teacher AS (
            SELECT DISTINCT ON (teacher.user_id)
                   teacher.user_id,
                   claim.identity_fingerprint
            FROM teacher_identity_claims claim
            JOIN teacher_profiles teacher ON teacher.id = claim.teacher_id
            JOIN kyc_requests request ON request.teacher_id = teacher.id
            WHERE request.identity_status = 'VERIFIED'
              AND request.identity_verified_at IS NOT NULL
            ORDER BY teacher.user_id, request.identity_verified_at DESC, request.id
        ), legacy AS (
            SELECT student.user_id, student.identity_fingerprint
            FROM student_profiles student
            WHERE student.identity_verified_at IS NOT NULL
              AND student.identity_fingerprint IS NOT NULL
            UNION ALL
            SELECT user_id, identity_fingerprint FROM latest_teacher
        )
        SELECT 1
        FROM legacy
        GROUP BY user_id
        HAVING COUNT(DISTINCT identity_fingerprint) > 1
    ) THEN
        RAISE EXCEPTION 'One account has conflicting legacy CCCD fingerprints; manual review is required';
    END IF;
END $$;

WITH latest_teacher AS (
    SELECT DISTINCT ON (teacher.user_id)
           teacher.user_id,
           claim.identity_fingerprint,
           COALESCE(NULLIF(BTRIM(request.ekyc_provider), ''), 'VNPT_EKYC_WEB_SDK') AS provider,
           NULLIF(BTRIM(request.server_full_name), '') AS full_name,
           CASE
               WHEN request.server_date_of_birth ~ '^\d{4}-\d{2}-\d{2}$'
                   THEN request.server_date_of_birth::DATE
               ELSE NULL
           END AS date_of_birth,
           request.identity_verified_at AS verified_at,
           'TEACHER'::VARCHAR(16) AS source_subject
    FROM teacher_identity_claims claim
    JOIN teacher_profiles teacher ON teacher.id = claim.teacher_id
    JOIN kyc_requests request ON request.teacher_id = teacher.id
    WHERE request.identity_status = 'VERIFIED'
      AND request.identity_verified_at IS NOT NULL
    ORDER BY teacher.user_id, request.identity_verified_at DESC, request.id
), legacy AS (
    SELECT student.user_id,
           student.identity_fingerprint,
           COALESCE(NULLIF(BTRIM(student.identity_provider), ''), 'VNPT_EKYC_WEB_SDK') AS provider,
           NULLIF(BTRIM(student.identity_full_name), '') AS full_name,
           student.identity_date_of_birth AS date_of_birth,
           student.identity_verified_at AS verified_at,
           'STUDENT'::VARCHAR(16) AS source_subject
    FROM student_profiles student
    WHERE student.identity_verified_at IS NOT NULL
      AND student.identity_fingerprint IS NOT NULL
    UNION ALL
    SELECT * FROM latest_teacher
), ranked AS (
    SELECT legacy.*,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY verified_at DESC, source_subject, identity_fingerprint
           ) AS row_number
    FROM legacy
)
INSERT INTO account_identity_verifications (
    user_id,
    identity_fingerprint,
    provider,
    full_name,
    date_of_birth,
    verified_at,
    source_subject
)
SELECT user_id,
       identity_fingerprint,
       provider,
       full_name,
       date_of_birth,
       verified_at,
       source_subject
FROM ranked
WHERE row_number = 1;
