-- ============================================================================
-- V038__create_teacher_certificate_claims_table.sql
-- MHB-13 / UC-28: Prevent one JLPT certificate from being claimed by
-- different teacher profiles. The original certificate remains in restricted
-- KYC storage; this table stores only a normalized lookup value.
-- ============================================================================

CREATE TABLE teacher_certificate_claims (
    id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id                  UUID         NOT NULL REFERENCES teacher_profiles (id),
    kyc_request_id              UUID         NOT NULL REFERENCES kyc_requests (id),
    certificate_type            VARCHAR(20)  NOT NULL DEFAULT 'JLPT',
    normalized_certificate_code VARCHAR(100) NOT NULL,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ,

    CONSTRAINT chk_teacher_certificate_claims_type
        CHECK (certificate_type IN ('JLPT')),
    CONSTRAINT uk_teacher_certificate_claims_type_code
        UNIQUE (certificate_type, normalized_certificate_code)
);

CREATE INDEX idx_teacher_certificate_claims_teacher_id
    ON teacher_certificate_claims (teacher_id);

CREATE INDEX idx_teacher_certificate_claims_request_id
    ON teacher_certificate_claims (kyc_request_id);
