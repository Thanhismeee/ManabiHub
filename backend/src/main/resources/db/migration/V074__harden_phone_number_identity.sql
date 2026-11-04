-- V069 has already shipped and is immutable. Apply subsequent hardening as a
-- forward-only migration so the released Flyway checksum remains valid.
ALTER TABLE app_users
    ALTER COLUMN phone_number TYPE VARCHAR(20);

-- Values created after V069 can still differ only by whitespace or +84 form.
-- Rebuild the index around canonicalization and deterministic cleanup.
DROP INDEX IF EXISTS uq_app_users_phone_number;

UPDATE app_users
SET phone_number = CASE
    WHEN NULLIF(BTRIM(phone_number), '') IS NULL THEN NULL
    WHEN BTRIM(phone_number) LIKE '+84%' THEN '0' || SUBSTRING(BTRIM(phone_number) FROM 4)
    ELSE BTRIM(phone_number)
END;

DO $$
DECLARE
    conflicting_verified_numbers INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO conflicting_verified_numbers
    FROM (
        SELECT phone_number
        FROM app_users
        WHERE phone_number IS NOT NULL
          AND phone_verified_at IS NOT NULL
        GROUP BY phone_number
        HAVING COUNT(*) > 1
    ) conflicts;

    IF conflicting_verified_numbers > 0 THEN
        RAISE EXCEPTION
            'V074 blocked: % canonical phone number(s) belong to multiple verified accounts; manual review is required before retrying',
            conflicting_verified_numbers;
    END IF;
END
$$;

WITH ranked_phone_claims AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY phone_number
               ORDER BY (phone_verified_at IS NOT NULL) DESC,
                        created_at ASC NULLS LAST,
                        id ASC
           ) AS claim_rank
    FROM app_users
    WHERE phone_number IS NOT NULL
), duplicate_unverified_claims AS (
    SELECT ranked.id
    FROM ranked_phone_claims ranked
    JOIN app_users users ON users.id = ranked.id
    WHERE ranked.claim_rank > 1
      AND users.phone_verified_at IS NULL
)
UPDATE app_users users
SET phone_number = NULL
FROM duplicate_unverified_claims duplicates
WHERE users.id = duplicates.id;

CREATE UNIQUE INDEX uq_app_users_phone_number
    ON app_users (phone_number)
    WHERE phone_number IS NOT NULL AND BTRIM(phone_number) <> '';
