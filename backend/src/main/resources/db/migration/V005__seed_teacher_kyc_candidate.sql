-- ============================================================================
-- V005__seed_teacher_kyc_candidate.sql
-- MHB-11 / UC-22: Demo teacher candidate for KYC submission evidence
--
-- Keep this additive so existing Flyway checksums for earlier migrations remain
-- stable in local environments that have already run V003/V004.
-- ============================================================================

INSERT INTO app_users (id, email, full_name, provider, provider_user_id, user_status)
VALUES (
    'd0000000-0000-0000-0000-000000000003',
    'teacher.candidate@manabihub.local',
    'Demo Teacher Candidate',
    'GOOGLE',
    'google-teacher-candidate-001',
    'ACTIVE'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
VALUES (
    'd0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000002'
)
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO teacher_profiles (id, user_id, display_name, bio, kyc_status, can_publish_course)
VALUES (
    'e0000000-0000-0000-0000-000000000003',
    'd0000000-0000-0000-0000-000000000003',
    'Teacher Candidate Demo',
    'Teacher candidate used for UC-22 KYC submission flow.',
    'NOT_SUBMITTED',
    FALSE
)
ON CONFLICT (id) DO NOTHING;
