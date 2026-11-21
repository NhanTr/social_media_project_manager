CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE users ADD COLUMN phone_lookup_hash CHAR(64);

WITH normalized AS (
    SELECT
        id,
        CASE
            WHEN compact_phone ~ '^0[0-9]{9}$'
                THEN '+84' || substring(compact_phone FROM 2)
            WHEN compact_phone ~ '^84[0-9]{9}$'
                THEN '+' || compact_phone
            WHEN compact_phone ~ '^\+84[0-9]{9}$'
                THEN compact_phone
            WHEN compact_phone ~ '^\+[0-9]{8,15}$' AND compact_phone NOT LIKE '+84%'
                THEN compact_phone
        END AS canonical_phone
    FROM (
        SELECT id, regexp_replace(btrim(phone_number), '\s+', '', 'g') AS compact_phone
        FROM users
        WHERE phone_number IS NOT NULL
    ) phones
)
UPDATE users
SET phone_lookup_hash = encode(digest(normalized.canonical_phone, 'sha256'), 'hex')
FROM normalized
WHERE users.id = normalized.id
  AND normalized.canonical_phone IS NOT NULL;

CREATE UNIQUE INDEX uq_users_phone_lookup_hash
    ON users(phone_lookup_hash)
    WHERE phone_lookup_hash IS NOT NULL;
