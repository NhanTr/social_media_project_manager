# LC-S4-03 mobile subscription contract — proposed

Status on 12 November 2026: **proposed mobile integration contract**. No subscription/payment service or Gateway route exists in fetched `origin/dev` (`ea52c38`) or another fetched branch. LC-S4-01 and LC-S4-02 remain In Progress. The Flutter implementation uses these paths but reports unavailable/locks AI until they exist. This document does not claim a working payment provider or webhook. Machine-readable draft: [s4-mobile-subscription.yaml](s4-mobile-subscription.yaml).

## Ownership and authentication
All requests use the existing ApiClient: Gateway `/api/v1/**`, user Bearer JWT, Terms gate, single-flight refresh and `X-Request-Id`. Identity comes from the JWT, never a body user ID. Subscription is the sole owner of tiers/entitlements; payment owns checkout and verified webhook processing. Neither mobile nor AI reads/writes subscription tables. Implementing those owners is LC-S4-01/02, outside LC-S4-03.

## Catalog and account
`GET /api/v1/subscriptions/plans` returns `{items: [...]}` with exactly one FREE and one PLUS:

```json
{"tier":"PLUS","name":"Plus","priceLabel":"<server-formatted price>","sandbox":true,"checkoutEnabled":true,"benefits":["<server-defined benefit>"]}
```

No price is hard-coded in production Flutter. The Vietnamese amount in HTTP test fixtures is an example only. Mobile supports **sandbox checkout only** in this delivery; `sandbox:false` disables checkout even when `checkoutEnabled:true`.

`GET /api/v1/subscriptions/me` returns:

```json
{
  "tier": "PLUS",
  "status": "ACTIVE",
  "paymentStatus": "SUCCEEDED",
  "paymentSessionId": "opaque-checkout-session",
  "cancelAtPeriodEnd": false,
  "currentPeriodEnd": "2026-12-12T12:00:00Z",
  "validUntil": "2026-11-12T12:05:00Z",
  "entitlements": {"aiGeneration": true}
}
```

`status`: ACTIVE, GRACE, PAST_DUE, CANCELED, EXPIRED, INCOMPLETE. `paymentStatus`: NONE, PENDING, FAILED, SUCCEEDED. `paymentSessionId` correlates pending/terminal payment state with a checkout; nullable only for NONE. `currentPeriodEnd` may be null when no billed period exists. Timestamps require timezone. Server `validUntil` bounds entitlement freshness and must not exceed the entitlement's effective expiry; use short TTLs. Responses must be account-scoped with `Cache-Control: no-store`; no intermediary may mix users or serve stale entitlement snapshots.

Mobile uses the explicit boolean grant plus snapshot validity. It does not derive grants from the tier name, a redirect URL, launch result, or payment label. Catalog/snapshot errors fail closed for AI and purchases while retaining any old snapshot only as visibly stale display data. The server must compute consistent lifecycle and entitlement values. Backend AI/Post enforcement remains mandatory even when the UI is disabled.

## Checkout and retry
`POST /api/v1/subscriptions/checkout`:

```json
{"tier":"PLUS","clientRequestId":"<UUID-v4>"}
```

201 (or replay 200) returns:

```json
{"sessionId":"opaque-checkout-session","url":"https://<sandbox-provider>/...","sandbox":true,"expiresAt":"2026-11-12T12:30:00Z"}
```

Requirements for LC-S4-02:
- Deduplicate `(authenticated user, clientRequestId)` atomically; replay must return the same session. A changed payload for the same key returns 409. Retain keys beyond the provider retry/session lifetime.
- Enforce one pending checkout per user/tier and reuse it for a new key after app restart. Mobile preserves the request UUID on uncertain retries in the current account controller and reuses unexpired sessions; it does not persist financial session URLs or tokens locally.
- Return a server-controlled HTTPS sandbox provider URL, without embedded credentials. Never accept a provider URL or return-success flag from the client. Mobile rejects unsafe, expired and non-sandbox sessions before launch.
- Configure provider browser return on the backend/provider side. Returning to the app (or opening it manually) triggers a refresh; no custom success callback is required or trusted by mobile. Cold-start persistence is read from server payment state.
- Only verified, idempotent, order-aware webhook processing may grant/revoke tier/entitlements. Out-of-order terminal states for older sessions must not replace the current pending session.

Mobile reconciles immediately on paywall app resume, then polls at most four more times, three seconds apart, while payment is pending. Manual restore/refresh remains available after the bounded window. Failure does not log out/deactivate an account or claim payment succeeded. An old terminal payment cannot clear a newer locally pending checkout; correlation uses `paymentSessionId`.

## Cancellation and restore
`POST /api/v1/subscriptions/me/cancel` with `{"cancelAtPeriodEnd":true}` returns 200/202/204. Operation is idempotent. Mobile asks for confirmation, then GETs the authoritative snapshot; it never locally downgrades tier or computes a cancellation effective date. Ambiguous cancellation failure asks the user to refresh before retrying.

“Khôi phục / Làm mới quyền lợi” means reload this account's server entitlement and payment state. It is **not** Apple/Google in-app-purchase restoration. Platform billing and store production policy are separate decisions before release; this task only supports the specified payment sandbox.

## Existing AI API
Mobile's trial screen calls the existing `POST /api/v1/ai/generations` with `{"stylePrompt":"...","generationType":"TEXT_TO_IMAGE"}` and GET `/api/v1/ai/generations/{id}`. It refreshes the account before each request, checks an unexpired explicit grant, and fails closed on authorization errors. Prompt length is 1–1000 after trim. No automatic create/retry loop exists because the current AI create API has no idempotency contract. Result status and an explicit status-refresh control are shown; SUCCESS expects a secure media URL.

The existing AI provider is a mock and does **not yet enforce subscription entitlement** at the service boundary. The UI identifies trial output; do not present it as paid production AI. LC-S4-01 integration must enforce authorization in AI/Post and deny stale/missing entitlement before real rollout. Closing a screen during a request does not cancel a server-created AI job; job recovery/listing remains a separate API gap.

## Errors and acceptance remaining
Use existing ProblemDetail: 400 validation, 401 expired session, 403 entitlement/Terms, 409 state/request conflict, 410 expired checkout, 429 rate limit, 503 unavailable. Never put provider secrets, JWTs or customer details into mobile errors/logs.

Before card closure, validate this proposed schema with backend owners, route/authentication through Gateway, real sandbox browser launch/return on Android/iOS, webhook pending→grant and failed/refund/out-of-order behavior, account restart, cancel-at-period-end effective expiry, and service-side AI authorization. Fixture tests exercise mobile state transitions; they are not live provider evidence.
