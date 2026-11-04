-- Temporary ownership-verification state for the local/test student payout
-- simulation. The columns intentionally use provider-neutral names so a real
-- verification adapter can replace MOCK_LOCAL without another data rewrite.

ALTER TABLE student_bank_accounts
    ADD COLUMN ownership_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ownership_verification_method VARCHAR(50),
    ADD COLUMN ownership_verified_at TIMESTAMPTZ;

ALTER TABLE student_bank_accounts
    ADD CONSTRAINT chk_student_bank_ownership_verification
        CHECK (
            (ownership_verified = FALSE
                AND ownership_verification_method IS NULL
                AND ownership_verified_at IS NULL)
            OR
            (ownership_verified = TRUE
                AND ownership_verification_method IS NOT NULL
                AND ownership_verified_at IS NOT NULL)
        );
