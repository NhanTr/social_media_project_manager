ALTER TABLE notifications ADD COLUMN idempotency_key VARCHAR(100);
CREATE UNIQUE INDEX uq_notifications_idempotency_key
    ON notifications(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
GRANT SELECT, INSERT, UPDATE, DELETE ON notifications TO notification_service_user;
