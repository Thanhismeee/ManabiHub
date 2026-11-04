-- ============================================================================
-- V004__extend_kyc_submission_metadata.sql
-- MHB-11 / UC-22: Teacher KYC submission metadata
-- ============================================================================

ALTER TABLE kyc_requests
    ADD COLUMN certificate_code VARCHAR(100),
    ADD COLUMN copyright_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN verification_payload JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE kyc_documents
    ADD COLUMN file_hash VARCHAR(128);

CREATE INDEX idx_kyc_documents_file_hash ON kyc_documents (file_hash);
