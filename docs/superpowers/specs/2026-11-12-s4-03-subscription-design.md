# LC-S4-03 Flutter paywall and tier management

Source: https://trello.com/c/ESGenxaf. Initial base: freshly fetched `origin/dev` at `eefc3a5`. Rebased onto `ea52c38` after Post, Chat, Notification and Flutter S3 PRs merged during implementation; retained both S3 and subscription shell integration.

## Intent and scope
Expose Free/Plus comparison, sandbox checkout, entitlement refresh, cancel at period end, payment pending/failure, and an entitlement-gated entry to the existing AI generation API. Preserve account/session boundaries and existing photo/feed flows. Prices and benefits come from the server; no invented production price or client-granted tier.

## Constraints and dependencies
LC-S4-01 and LC-S4-02 are In Progress and absent from all fetched refs on 12 November 2026. The mobile API contract added with this task is **proposed**, not evidence that backend endpoints exist. Real checkout/webhook and backend AI authorization must be verified after those tasks land. Missing endpoints show a retryable unavailable screen and keep AI locked. No payment credentials, live payments, backend domain changes, or merge to shared branches.

## Approaches
1. Wait for both backend tasks: avoids provisional DTOs but leaves all independent Flutter work undone.
2. Build the HTTP client and mobile flow against an explicit proposed contract (selected implementation decision): provides reviewable work now; DTO/path alignment and live acceptance remain integration work.
3. Ship a demo repository: convenient preview, but a fake successful payment could misrepresent access. Rejected in favor of HTTP-boundary fixtures restricted to tests.

## Behavior
- Profile opens the paywall. Catalog contains FREE and PLUS with server-supplied benefits and price labels, explicitly marked sandbox.
- GET account snapshot supplies tier, subscription lifecycle, payment status, cancellation flag, period end, explicit `aiGeneration` entitlement and `validUntil`. Unknown/malformed state fails closed.
- Checkout uses a stable client request UUID for uncertain retries, reuses an unexpired session, and launches only HTTPS sandbox URLs in the external browser. It never changes the account tier or AI entitlement.
- App resume and manual refresh reload server state. Pending payment is reconciled with bounded polling, never an infinite spinner. The user can keep refreshing after delayed webhook delivery. Closing the browser is not payment success.
- Cancellation needs an in-app confirmation and waits for a fresh server snapshot; existing access lasts only as the server authorizes it. Renewal is displayed using period-end data.
- AI access checks the explicit entitlement and freshness, including before generation. Neither PLUS text nor callback parameters grant access. Existing backend AI is a mock provider; label it as a trial and do not describe it as production generation.
- One controller per authenticated account, disposed on logout/account replacement. No entitlement persistence across sessions; late network responses cannot repopulate disposed state.

## Validation
HTTP boundary tests, controller tests, widget tests and a checkout-return/delayed-webhook test with real controller/API and fixture HTTP transport; full Flutter suite, analyzer, format, debug build where tooling permits, repository policy tests, diff check. Live payment/webhook and device browser return remain explicitly pending while dependencies are absent.
