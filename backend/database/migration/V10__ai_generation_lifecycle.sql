-- Durable database-backed demo worker; the generation itself is its idempotent mock charge key.
ALTER TABLE ai_generations
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN attempt_token UUID,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    ADD COLUMN mock_charge_count INTEGER NOT NULL DEFAULT 0 CHECK (mock_charge_count IN (0, 1));
CREATE INDEX idx_ai_generations_worker ON ai_generations(status, lease_until, id)
    WHERE status IN ('PENDING', 'PROCESSING');
