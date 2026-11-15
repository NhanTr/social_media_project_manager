# LC-S5-01 — security and retention MVP

## Ingress, trace and telemetry

Every MVC service scans common-lib. `RequestIdFilter` runs before Spring Security, accepts only `[A-Za-z0-9._-]{1,64}` and otherwise generates a UUID. It replaces the effective request header, sets the response header and request attribute, scopes MDC/ThreadLocal to the request and clears both in `finally`. Gateway routing therefore forwards the normalized ID. All internal synchronous RestClients use `RequestTracing.restClientBuilder()`; new clients must use this helper. Scheduled tasks have no incoming trace and do not invent an upstream request ID.

`RequestTelemetryFilter` logs a fixed key/value event with request ID, allowlisted HTTP method, allowlisted route category, numeric status and duration. It does not log URL/query, header collections, request/response body, payment signatures/tokens, OTP, email, phone or principal. Micrometer `loccoc.http.requests` timers expose aggregate count, duration and maximum by method/category/status only, with no user/request-ID cardinality. Standard Actuator health and runtime/JVM metrics remain available. Expose health/info/metrics in deployment config; keep metrics private to operators rather than proxying them as a user API. Expected health endpoint is `/actuator/health` on every service. Unexpected exception logging records exception type and sanitized request ID only, never exception message or stack, since database/client exceptions can contain credentials or PII. Mock email/push delivery logs contain generated message IDs, outcome and enum template only; they are still mock providers.

Gateway `InternalEndpointDenyFilter` returns 404 for `/internal` and `/internal/**` even with a valid internal key. Downstream endpoints continue to require `ROLE_SERVICE` from `X-Internal-Api-Key`; network isolation must prevent direct internet access to service ports. No gateway route for internal paths is permitted.

`ActiveAccountFilter` checks authenticated user REST requests against `GET /internal/v1/users/{id}/active` (`{active:boolean}`), so an already issued JWT stops working immediately after ban/deletion. Inactive returns 403 `ACCOUNT_INACTIVE`; identity timeout/unavailability returns 503 `ACCOUNT_STATE_UNAVAILABLE`. Identity URL is `clients.identity.base-url`, falling back to `IDENTITY_SERVICE_URL` then `http://localhost:8081`; use the internal Docker service URL in Compose. Connect/read timeouts are 1/2 seconds. Internal calls do not recurse; admin/service principals, public auth/latest terms and sandbox webhook/checkout routes bypass the account-state check. This is a synchronous MVP dependency, not a cache/distributed revocation system. Chat checks the same bounded internal API on CONNECT, SEND and SUBSCRIBE, and again before outbound MESSAGE delivery to an established session. Banned or unavailable accounts cannot continue sending/subscribing/receiving on an existing session. Session tracking is capped at 10,000 and disconnect events release entries. No cached active result survives a ban.

## Bounded local rate limits

Common `LocalRateLimitFilter` runs after authentication and before controllers. Default budgets are 120 public API requests/minute and 600 internal API requests/minute, configured with `security.rate-limit.public-per-minute` and `security.rate-limit.internal-per-minute`. Identity uses authenticated principal (not supplied user headers), otherwise the socket peer address. It never trusts `X-Forwarded-For`. A maximum of 10,000 identities (`security.rate-limit.maximum-identities`) is held per process/window; new identities are denied if full, rather than growing memory. A fixed UTC minute resets memory. Rejections return 429 `RATE_LIMITED` with `Retry-After` seconds. Health/info/metrics are excluded. Unauthenticated requests rejected by the security chain are denied before this application limiter.

This is single-instance fixed-window MVP protection. Gateway and downstream each enforce their own budgets; internal SERVICE callers share a budget. Deployments behind a proxy share the socket IP for anonymous traffic. Production needs a trusted-proxy policy and distributed edge limiter with abuse-specific login/payment/upload quotas. No rate limit identity is logged.

## Account and media retention

Policy: account deletion request deactivates the account and schedules retention after the existing 30-day grace period. Identity owns deletion requests and user PII; Post alone updates posts/media/upload rows and deletes managed MinIO objects. No worker writes another domain's tables. Existing user rows are retained as inactive anonymized tombstones for foreign keys. Chat messages, social relationships, notifications, moderation/audit and financial records are retained under their current domain policies; this MVP is not a complete regulatory erasure system. Remote/external media URLs cannot be erased by the managed MinIO adapter.

Safety defaults:

- Account scheduler: `retention.accounts.enabled=false`, `retention.accounts.dry-run=true`.
- Physical media cleanup and orphan expiry: `retention.media.dry-run=true`.
- All manual internal retention endpoints default `dryRun=true`; writing requires explicitly passing `dryRun=false`.

Internal endpoints (direct service only, `X-Internal-Api-Key` required):

| Owner | Endpoint | Result |
| --- | --- | --- |
| Identity | `POST /internal/v1/retention/accounts/run?dryRun=true` | bounded outcome counts: DRY_RUN, NOT_DUE, MEDIA_PENDING, COMPLETED, FAILED_RETRYABLE |
| Post | `POST /internal/v1/retention/accounts/{userId}?dryRun=true` | `{posts,uploads,complete,dryRun}`; at most 100 posts + 100 uploads |

Dry-run reads candidates only: it never changes status, retry count, queue/storage, PII or deletion progress and makes no cross-domain cleanup request from Identity. Post preview counts candidates and pending managed cleanup; it does not contact object storage. The account worker locks one due deletion request per transaction; each tick processes at most 25 requests. The owner API soft-deletes up to 100 account posts, expires up to 100 account uploads and durably enqueues managed URLs/object keys. Calls are replayable: already DELETED/EXPIRED rows are excluded. Identity keeps the request PENDING while Post has more batches or any unfinished `users/{id}/` queue item, including exhausted storage failures. Only after Post confirms physical managed-media cleanup does Identity clear email, phone/hash, Google ID, profile/avatar/bio, invite and login time, assign a non-identifying username, set deletedAt/INACTIVE and mark COMPLETED. A timeout or owner failure leaves PENDING and is retried without claiming success.

The media scheduler handles at most 25 queue items per tick and expires at most 100 unconsumed orphan sessions per tick. Active/HIDDEN post media or thumbnail references survive physical cleanup; consumed uploads survive orphan expiry. Failed object removals use a fixed safe error code and bounded five-attempt backoff. Exhausted entries stay pending and require operator inspection/requeue; they are excluded from normal work selection so they cannot starve newer items. If another active post references a deleted account's managed media, cleanup defers and the account remains MEDIA_PENDING for manual policy resolution.

For a controlled apply, first preview with direct internal APIs, review counts in an isolated/staging environment, then configure media dry-run false and account enabled true/accounts dry-run false. Monitor pending/exhausted outcomes and object deletion evidence. Never enable apply against user/local existing data merely to verify the feature. Tests use disposable Testcontainers PostgreSQL/MinIO only. Object deletion cannot be rolled back; backup retention/restore policy and third-party purge are separate operational responsibilities.

## Secret rotation runbook

1. Inventory JWT HMAC secret, internal API key, webhook signing secret, phone lookup HMAC key, PostgreSQL credentials, Redis authentication and MinIO credentials in the secret store. Keep environment-specific values; local defaults are not production credentials. Do not paste values in commands, logs or tickets.
2. Stage fresh random values in the deployment secret manager and validate names/length without printing them. This MVP has one active key per purpose, so schedule coordinated restart/maintenance rather than assuming dual-key overlap exists.
3. JWT rotation: restart all issuers/verifiers with the new secret together. Old access/admin tokens fail verification; revoke identity Redis refresh sessions and require login again. Do not restore the compromised signing key to recover access.
4. Internal-key rotation: update all service clients and verifiers atomically, pause background workers during mismatch, then resume after direct internal health probes and a normal user request succeed. Gateway must still deny internal URLs with either key.
5. Payment webhook rotation: coordinate provider/adapter signing secret and subscription verifier, pause event ingress during mismatch, replay signed events through the durable deduplication path after validation. Never log raw signature, checkout secret or payload as debugging evidence.
6. Rotate database/Redis/MinIO users with least privilege and staged provider credentials where supported, restart connection pools, test health and one non-destructive read. Retire old credentials only after consumers switch. Phone lookup HMAC changes require a controlled index rehash/migration; replacing it blindly makes existing phone lookups inconsistent.
7. Verify health, aggregate request/error/rate-limit metrics and trace continuity using sanitized IDs. Record secret version identifiers and completion time, never secret values. On failure, stop writers and fix coordinated config; restoring a known compromised credential is not a normal rollback.

## Verification evidence

Focused suites cover malformed ID replacement and effective forwarding header, outbound RestClient propagation, literal log capture excluding secrets/PII, exception redaction, public/internal independent budgets and memory cap, spoofed forwarded header rejection, active-account fail-closed behavior, gateway internal denial, account grace/dry-run/downstream failure/tombstone, media dry-run and PostgreSQL account owner cleanup/replay. Existing real MinIO lifecycle test opts into `retention.media.dry-run=false`; runtime provider delivery and distributed production limits are not claimed by these tests.


## Identity tombstone database invariant

V12 preserves the original provider-identifier requirement for live accounts and permits missing phone/Google ID only for an INACTIVE row with deleted_at set. This allows retention to clear PII while retaining foreign-key references. IdentitySchemaIntegrationTests exercises Hibernate/Flyway validation and proves an anonymized tombstone cannot be reactivated without a provider identifier. The full Gateway harness verifies exact dry-run row equality and a successful explicit apply in its disposable database.

Hibernate SQL error detail logging is disabled (`org.hibernate.orm.jdbc.error=OFF`) because PostgreSQL constraint detail may echo a row containing PII. API errors retain only sanitized category/code/request ID through the common handler; operational diagnostics must not re-enable raw row logging with real account data.
