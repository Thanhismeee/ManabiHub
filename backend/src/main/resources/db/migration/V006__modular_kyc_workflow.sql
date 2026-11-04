-- ============================================================================
-- V006__modular_kyc_workflow.sql
-- MHB-11 / UC-22: Modular teacher verification workflow
-- ============================================================================

ALTER TABLE kyc_requests
    ADD COLUMN provider_session_id VARCHAR(255),
    ADD COLUMN provider_transaction_id VARCHAR(255),
    ADD COLUMN identity_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN identity_verified_at TIMESTAMPTZ,
    ADD COLUMN certificate_status VARCHAR(30) NOT NULL DEFAULT 'LOCKED',
    ADD COLUMN certificate_submitted_at TIMESTAMPTZ;

ALTER TABLE kyc_requests
    DROP CONSTRAINT chk_kyc_requests_status;

ALTER TABLE kyc_requests
    ADD CONSTRAINT chk_kyc_requests_status
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CORRECTION_REQUIRED')),
    ADD CONSTRAINT chk_kyc_identity_status
        CHECK (identity_status IN ('NOT_STARTED', 'PROCESSING', 'VERIFIED', 'FAILED')),
    ADD CONSTRAINT chk_kyc_certificate_status
        CHECK (certificate_status IN ('LOCKED', 'NOT_SUBMITTED', 'PENDING_REVIEW', 'APPROVED', 'REJECTED'));

CREATE INDEX idx_kyc_requests_identity_status ON kyc_requests (identity_status);
CREATE INDEX idx_kyc_requests_certificate_status ON kyc_requests (certificate_status);
