# LC-S4-01 / LC-S4-02 — Subscription and local payment sandbox

Implemented 15 November 2026 on feature/LC-S4-backend-closeout, extending the mobile proposal. Java/Spring subscription-service listens on :8088. It alone writes subscription_accounts, subscription_checkouts, subscription_checkout_keys, subscription_events and subscription_audit. Migration V9 grants the service append-only access to events, keys and audit. Other services consume internal REST, never these tables.

## Public and internal contracts
- GET /api/v1/subscriptions/plans → {items:[FREE,PLUS]}. Prices explicitly say sandbox/no real money.
- GET /api/v1/subscriptions/me → tier/status/paymentStatus/paymentSessionId/cancelAtPeriodEnd/currentPeriodEnd/validUntil/entitlements.aiGeneration/effectiveAt. All subscription responses use Cache-Control: no-store.
- POST /api/v1/subscriptions/checkout {tier:PLUS,clientRequestId:UUID} → 200 {sessionId,url,sandbox:true,expiresAt}. New keys reuse the same unexpired pending session. Expired replays retain their original expired timestamp, so clients cannot launch them. Unsupported tier is rejected.
- POST /api/v1/subscriptions/me/cancel {cancelAtPeriodEnd:boolean} → current snapshot. Idempotent; cancellation never locally shortens a paid period.
- POST /api/v1/admin/subscriptions/{userId}/override {tier,currentPeriodEnd?,note} → snapshot. Admin role and nonblank note mandatory; PLUS requires a future period end. Immutable audit includes actor, time and before/after tier.
- GET /internal/v1/subscriptions/{userId}/entitlements → {aiGeneration,validUntil}; requires X-Internal-Api-Key and SERVICE principal; no Gateway route.
- POST /api/v1/payments/webhook → signed event (below). This is the only payment-driven mutation entry point.

User endpoints enforce USER JWT + accepted Terms. Account state is rechecked through Identity by common ActiveAccountFilter, both at Gateway and downstream. Snapshot grants expire at the earlier of period end and 60 seconds. Default FREE provisioning is atomic. A canceled period becomes FREE/CANCELED at period end; uncanceled expiry becomes FREE/EXPIRED. Failed renewal retains the existing paid period as GRACE, never extends it and never disables the account. Admin FREE override is immediate. Provider CANCELED/REFUNDED revokes only the period actually sourced by that checkout, preserving any newer pending checkout and unrelated paid/admin grant.

## Webhook
Body JSON: {eventId,sessionId,type,occurredAt,periodEnd?}. Types: SUCCEEDED, FAILED, REFUNDED, CANCELED. SUCCEEDED requires periodEnd after occurredAt and within 366 days. Future occurredAt beyond five minutes is rejected. Input timestamps are canonicalized to microseconds for stable PostgreSQL replay.

Header X-Payment-Signature: t=<epoch-seconds>,v1=<hex HMAC-SHA256(secret, timestamp + "." + exact UTF-8 body)>.

The verifier uses constant-time byte comparison and a five-minute delivery timestamp tolerance. Invalid signature/JSON/body over 16 KiB returns 400 before any mutation. Event ID is globally unique; same canonical payload is a no-op, changed payload returns 409. Account row locks serialize checkout, webhook and override across processes; event persistence, account mutation and audit share one DB transaction. Session state and per-session event time reject delayed success after terminal failure/refund/expiry and prevent old sessions replacing new pending state. Concurrent duplicate events grant once. Signature timestamp is delivery time; occurredAt is business event time, allowing correctly signed delayed deliveries without trusting client callbacks.

## Hosted mock sandbox
The local adapter creates random opaque checkout capabilities and a hosted HTML form at /api/v1/payments/sandbox/checkouts/{sessionId}. Viewing the page never grants access. Its success/failure forms simulate a provider event, sign it server-side and pass the exact same webhook verifier/processor. It performs no real charge and contacts no third-party payment provider. Repeated identical form submissions create one canonical event. The page has no-referrer/no-store/CSP and no account identifiers, scripts or third-party assets.

Set SANDBOX_CHECKOUT_BASE_URL to the HTTPS ingress URL ending in /api/v1/payments/sandbox/checkouts; the development default expects a local TLS proxy on :8443. Compose Gateway itself remains HTTP behind TLS ingress. PAYMENT_WEBHOOK_SECRET must be at least 32 characters; never send it to mobile. External-provider browser/merchant credentials are deliberately unnecessary for this local sandbox. app.payment.sandbox-enabled controls hosted simulator registration, and is false in the prod profile. Production billing/provider integration remains out of scope and must not reuse the local adapter or signing key.

## Evidence
SubscriptionStateTests: FREE, expiry, cancellation clamp, failure account survival, refund. SubscriptionIntegrationTests: real PostgreSQL row locks, concurrent checkout and event dedup, default provisioning, audited overrides, failed-renewal grace, old-session/refund/new-checkout ordering, timestamp canonical replay, and signed hosted-sandbox boundary. SandboxPaymentProviderTests: raw-body tampering, missing/invalid/stale signatures and unsafe URL rejection. SubscriptionControllerTests: USER/Terms, admin/SERVICE authorization and invalid webhook cannot mutate.

Full integrated evidence and CI status are recorded in the branch review report. Tests of the hosted mock are not evidence of Stripe/PayPal/store billing. No automatic PR merge or Done transition.
