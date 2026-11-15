-- Subscription owns these tables. Identity and AI consume authenticated APIs only.
CREATE ROLE subscription_service_user LOGIN PASSWORD 'subscription_password';
GRANT CONNECT ON DATABASE social_media_ai_db TO subscription_service_user;
GRANT USAGE ON SCHEMA public TO subscription_service_user;
CREATE TABLE subscription_accounts (
 user_id BIGINT PRIMARY KEY CHECK (user_id > 0), tier VARCHAR(16) NOT NULL DEFAULT 'FREE' CHECK(tier IN ('FREE','PLUS')),
 status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE', payment_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
 payment_session_id VARCHAR(64), grant_session_id VARCHAR(64), cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
 current_period_end TIMESTAMPTZ, effective_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE subscription_checkouts (
 session_id VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES subscription_accounts(user_id),
 tier VARCHAR(16) NOT NULL CHECK(tier='PLUS'), status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL, event_at TIMESTAMPTZ,
 granted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX subscription_one_pending ON subscription_checkouts(user_id) WHERE status='PENDING';
CREATE TABLE subscription_checkout_keys (
 user_id BIGINT NOT NULL REFERENCES subscription_accounts(user_id), client_request_id UUID NOT NULL,
 tier VARCHAR(16) NOT NULL, session_id VARCHAR(64) NOT NULL REFERENCES subscription_checkouts(session_id),
 PRIMARY KEY(user_id,client_request_id)
);
CREATE TABLE subscription_events (
 event_id VARCHAR(128) PRIMARY KEY, session_id VARCHAR(64) NOT NULL REFERENCES subscription_checkouts(session_id),
 event_type VARCHAR(24) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, period_end TIMESTAMPTZ,
 processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE subscription_audit (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, actor_id BIGINT,
 action VARCHAR(40) NOT NULL, before_tier VARCHAR(16) NOT NULL, after_tier VARCHAR(16) NOT NULL,
 effective_at TIMESTAMPTZ NOT NULL, note VARCHAR(1000) NOT NULL
);
GRANT SELECT, INSERT, UPDATE ON subscription_accounts, subscription_checkouts TO subscription_service_user;
GRANT SELECT, INSERT ON subscription_checkout_keys, subscription_events, subscription_audit TO subscription_service_user;
GRANT USAGE, SELECT ON SEQUENCE subscription_audit_id_seq TO subscription_service_user;
