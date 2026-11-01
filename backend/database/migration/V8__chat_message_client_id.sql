-- Migration V8: Chat message clientMessageId idempotency and receipt tracking
ALTER TABLE messages
    ADD COLUMN client_message_id VARCHAR(100);

CREATE UNIQUE INDEX uq_messages_conversation_sender_client_id
    ON messages(conversation_id, sender_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

