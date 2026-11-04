-- One VNPT transaction can establish identity for exactly one ManabiHub account,
-- regardless of whether the entry point is the teacher or student KYC flow.
CREATE TABLE vnpt_identity_transaction_claims (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    subject_type VARCHAR(16) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_transaction_id VARCHAR(128) NOT NULL,
    provider_session_id VARCHAR(128),
    claimed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_vnpt_identity_claim_subject
        CHECK (subject_type IN ('STUDENT', 'TEACHER')),
    CONSTRAINT uq_vnpt_identity_claim_provider_transaction
        UNIQUE (provider, provider_transaction_id)
);

CREATE INDEX idx_vnpt_identity_claim_user
    ON vnpt_identity_transaction_claims (user_id, claimed_at DESC);

-- Preserve ownership of teacher transactions bound before this generic ledger
-- existed so they cannot later be replayed through the student endpoint.
INSERT INTO vnpt_identity_transaction_claims (
    id,
    user_id,
    subject_type,
    provider,
    provider_transaction_id,
    provider_session_id,
    claimed_at
)
SELECT gen_random_uuid(),
       teacher.user_id,
       'TEACHER',
       request.ekyc_provider,
       request.provider_transaction_id,
       request.provider_session_id,
       COALESCE(request.created_at, NOW())
FROM kyc_requests request
JOIN teacher_profiles teacher ON teacher.id = request.teacher_id
WHERE request.ekyc_provider IS NOT NULL
  AND request.provider_transaction_id IS NOT NULL
  AND BTRIM(request.provider_transaction_id) <> ''
ON CONFLICT (provider, provider_transaction_id) DO NOTHING;
