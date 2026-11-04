-- MHB-73: Add server-to-server VNPT verification support
-- Adds PENDING_SERVER_VERIFICATION enum value and server verification tracking columns

-- Add server verification tracking columns to kyc_requests
ALTER TABLE kyc_requests
    ADD COLUMN IF NOT EXISTS server_verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS server_verification_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS server_verification_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS server_verification_next_retry_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS server_full_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS server_date_of_birth VARCHAR(50);

-- Index for finding pending verification requests that need server confirmation
CREATE INDEX IF NOT EXISTS idx_kyc_requests_pending_server_verification
    ON kyc_requests (identity_status, server_verification_expires_at)
    WHERE identity_status = 'PENDING_SERVER_VERIFICATION';

-- MHB-73: Unique constraint to prevent cross-user and concurrent replay of VNPT transactions
CREATE UNIQUE INDEX IF NOT EXISTS uq_kyc_requests_provider_tx
    ON kyc_requests (ekyc_provider, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL AND provider_transaction_id != '';

-- MHB-73: Update check constraint to allow PENDING_SERVER_VERIFICATION
ALTER TABLE kyc_requests DROP CONSTRAINT IF EXISTS chk_kyc_identity_status;
ALTER TABLE kyc_requests ADD CONSTRAINT chk_kyc_identity_status CHECK (identity_status IN ('NOT_STARTED', 'PROCESSING', 'VERIFIED', 'FAILED', 'PENDING_SERVER_VERIFICATION'));
