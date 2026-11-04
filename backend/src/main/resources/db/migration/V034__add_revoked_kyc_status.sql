-- ============================================================================
-- V034__add_revoked_kyc_status.sql
-- MHB-13 / UC-28: Allow Course Manager to revoke an already-approved teacher
-- after a complaint. Adds the 'REVOKED' status to the KYC request and teacher
-- profile CHECK constraints so revocation can be persisted.
-- ============================================================================

ALTER TABLE kyc_requests
    DROP CONSTRAINT chk_kyc_requests_status;

ALTER TABLE kyc_requests
    ADD CONSTRAINT chk_kyc_requests_status
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CORRECTION_REQUIRED', 'REVOKED'));

ALTER TABLE teacher_profiles
    DROP CONSTRAINT chk_teacher_kyc_status;

ALTER TABLE teacher_profiles
    ADD CONSTRAINT chk_teacher_kyc_status
        CHECK (kyc_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED', 'CORRECTION_REQUIRED', 'REVOKED'));
