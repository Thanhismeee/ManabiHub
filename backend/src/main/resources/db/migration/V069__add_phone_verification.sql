-- Phone numbers are account-wide identities. Keep one canonical value and one
-- verified timestamp so both student and teacher profile endpoints share the
-- same uniqueness/immutability rule.
ALTER TABLE app_users
    ADD COLUMN phone_verified_at TIMESTAMPTZ;

UPDATE app_users
SET phone_number = NULLIF(BTRIM(phone_number), '')
WHERE phone_number IS NOT NULL;

UPDATE app_users
SET phone_number = '0' || SUBSTRING(phone_number FROM 4)
WHERE phone_number LIKE '+84%';

-- Legacy/demo data may contain the same phone on more than one account. Do not
-- delete either account and do not silently reassign a verified number. Keep a
-- deterministic owner (verified first, then oldest account) and clear only the
-- conflicting, unverified value so the unique index can be created safely.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM app_users
        WHERE phone_number IS NOT NULL
          AND phone_verified_at IS NOT NULL
        GROUP BY phone_number
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'V069 cannot resolve duplicate verified phone numbers safely; manual account review is required';
    END IF;
END $$;

WITH ranked_phone_numbers AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY phone_number
               ORDER BY (phone_verified_at IS NOT NULL) DESC,
                        created_at ASC NULLS LAST,
                        id
           ) AS duplicate_rank
    FROM app_users
    WHERE phone_number IS NOT NULL
)
UPDATE app_users user_row
SET phone_number = NULL
FROM ranked_phone_numbers ranked
WHERE user_row.id = ranked.id
  AND ranked.duplicate_rank > 1
  AND user_row.phone_verified_at IS NULL;

CREATE UNIQUE INDEX uq_app_users_phone_number
    ON app_users (phone_number)
    WHERE phone_number IS NOT NULL AND BTRIM(phone_number) <> '';

CREATE TABLE phone_verification_challenges (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    phone_number VARCHAR(20) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    nonce VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    resend_available_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_phone_verification_failed_attempts CHECK (failed_attempts >= 0)
);
