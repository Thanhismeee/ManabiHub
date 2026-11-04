-- Keep the released OTP tables and add provider-neutral, one-time challenge
-- identifiers. Firebase proofs never store the SMS code or ID token in the DB.
ALTER TABLE phone_verification_challenges
    ADD COLUMN challenge_id UUID,
    ADD COLUMN verification_method VARCHAR(20);

UPDATE phone_verification_challenges
SET challenge_id = gen_random_uuid(),
    verification_method = 'SMS';

ALTER TABLE phone_verification_challenges
    ALTER COLUMN challenge_id SET NOT NULL,
    ALTER COLUMN verification_method SET NOT NULL,
    ALTER COLUMN code_hash DROP NOT NULL,
    ALTER COLUMN nonce DROP NOT NULL;

CREATE UNIQUE INDEX uq_phone_verification_challenge_id
    ON phone_verification_challenges (challenge_id);

ALTER TABLE withdrawal_otp_challenges
    ADD COLUMN challenge_id UUID,
    ADD COLUMN verification_method VARCHAR(20),
    ADD COLUMN phone_number VARCHAR(20);

UPDATE withdrawal_otp_challenges
SET challenge_id = gen_random_uuid(),
    verification_method = 'EMAIL';

ALTER TABLE withdrawal_otp_challenges
    ALTER COLUMN challenge_id SET NOT NULL,
    ALTER COLUMN verification_method SET NOT NULL,
    ALTER COLUMN code_hash DROP NOT NULL,
    ALTER COLUMN nonce DROP NOT NULL;

CREATE UNIQUE INDEX uq_withdrawal_otp_challenge_id
    ON withdrawal_otp_challenges (challenge_id);

ALTER TABLE phone_verification_challenges
    ADD CONSTRAINT chk_phone_verification_method
        CHECK (verification_method IN ('SMS', 'FIREBASE')),
    ADD CONSTRAINT chk_phone_verification_proof_storage
        CHECK (
            (verification_method = 'SMS' AND code_hash IS NOT NULL AND nonce IS NOT NULL)
            OR
            (verification_method = 'FIREBASE' AND code_hash IS NULL AND nonce IS NULL)
        );

ALTER TABLE withdrawal_otp_challenges
    ADD CONSTRAINT chk_withdrawal_otp_method
        CHECK (verification_method IN ('EMAIL', 'FIREBASE')),
    ADD CONSTRAINT chk_withdrawal_otp_proof_storage
        CHECK (
            (verification_method = 'EMAIL' AND code_hash IS NOT NULL
                AND nonce IS NOT NULL AND phone_number IS NULL)
            OR
            (verification_method = 'FIREBASE' AND code_hash IS NULL
                AND nonce IS NULL AND phone_number IS NOT NULL)
        );
