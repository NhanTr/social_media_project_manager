-- Preserve provider identity requirements for live accounts while allowing
-- retention to remove PII from an inactive, explicitly deleted tombstone.
ALTER TABLE users DROP CONSTRAINT chk_users_auth_provider_identity;
ALTER TABLE users ADD CONSTRAINT chk_users_auth_provider_identity CHECK (
    (auth_provider = 'GOOGLE' AND google_id IS NOT NULL)
    OR (auth_provider = 'PHONE' AND phone_number IS NOT NULL)
    OR (status = 'INACTIVE' AND deleted_at IS NOT NULL
        AND google_id IS NULL AND phone_number IS NULL)
);
