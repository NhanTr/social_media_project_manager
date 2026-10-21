# Sprint 0–1 Remaining Automatic Gaps Design

## Goal

Complete the remaining code-verifiable acceptance gaps for LC-S1-01 and LC-S1-03 on PR #2 without claiming the manual device, OAuth, full-stack, architecture-review, or merge gates are complete.

## Scope

This change covers three related gaps:

1. Enforce acceptance of the latest Terms version before a normal user can call business APIs through the API Gateway.
2. Record security-relevant authentication events without logging OTPs, tokens, phone numbers, email addresses, or other sensitive payloads.
3. Serialize `block` against `accept friend request` so the database cannot end with both an active block and an active friendship for the same pair.

Manual Android/iOS acceptance, Google OAuth credentials, running the complete Docker stack, architecture approval, branch protection review, PR review, PR merge, and Trello completion remain outside this automatic implementation slice.

## Terms Gate

### Token contract

User access tokens carry a Boolean `terms_accepted` claim representing whether the user accepted the latest active Terms version when the token was issued. Refresh tokens do not authorize business APIs and do not need this claim.

The shared authenticated principal exposes this value to the Gateway. Legacy access tokens without the claim are treated as not accepted, forcing the client to authenticate or refresh again instead of silently bypassing the gate. Admin and service principals are not subject to the user Terms gate.

### Gateway behavior

After bearer-token authentication, a Gateway filter rejects an authenticated normal user whose `terms_accepted` value is false for business routes. The following remain accessible:

- `/api/v1/auth/**`
- `/api/v1/terms/latest`
- `/api/v1/terms/accept`
- health and API documentation endpoints
- internal service requests and admin flows

The rejection uses HTTP 403 with stable error code `TERMS_ACCEPTANCE_REQUIRED` and the existing `ProblemDetail` envelope. The Gateway is the public enforcement point; internal `/internal/v1/**` traffic is unchanged.

### Consent refresh flow

Accepting Terms does not mutate an already issued JWT. After a successful `/api/v1/terms/accept`, Flutter immediately calls the existing refresh endpoint and stores the newly issued access/refresh pair. The new access token contains `terms_accepted=true`, after which navigation continues to the authenticated app shell. A failed refresh keeps the user on the Terms screen and presents a retryable error.

## Authentication Audit

Authentication audit is emitted as structured application log events, not a new database table. Events include an event name, outcome, user ID when available, and the existing request ID from MDC. They never include OTP values, raw tokens, phone numbers, email addresses, Google payloads, or internal keys.

Required events:

- OTP request accepted or rate-limited
- OTP verification succeeded, failed, or exceeded attempts
- Google login succeeded or failed verification
- refresh rotation succeeded or rejected
- logout revoked a refresh session or received an already absent session
- Terms acceptance succeeded

Tests capture log output or invoke the audit formatter directly and assert that secrets and supplied identifiers do not appear.

## Block/Accept Concurrency

`accept` already locks the target friend-request row. `block` will query matching pending friend requests with a pessimistic write lock before changing their status, deleting a friendship, and storing the block. Both paths therefore serialize on the same pending request:

- If `accept` commits first, `block` observes the accepted request and then removes the resulting friendship before committing the block.
- If `block` commits first, it changes the request away from `PENDING`; the waiting `accept` fails with `FRIEND_REQUEST_CLOSED`.

The block operation remains idempotent. Notification delivery stays after the state transition and is not used as proof of the transaction outcome.

## Files and Responsibilities

- `backend/identity-service/.../security/JwtService.java`: add and read the `terms_accepted` access-token claim.
- `backend/identity-service/.../service/AuthService.java`: issue claim-aware access tokens and emit authentication audit events.
- `backend/identity-service/.../service/TermsService.java`: emit Terms-acceptance audit event.
- `backend/common-lib/.../security/CurrentUser.java`: expose Terms acceptance on the shared principal.
- `backend/common-lib/.../security/BearerTokenFilter.java`: map the claim into the authenticated principal.
- `backend/api-gateway/.../filter/TermsAcceptanceFilter.java`: enforce the public business-route gate.
- `backend/api-gateway/.../config/SecurityConfig.java`: register the Terms filter after bearer authentication.
- `loccoc_application/lib/...`: refresh tokens after Terms acceptance and keep failures retryable.
- `backend/social-service/.../repository/FriendRequestRepository.java`: add the locked pair query.
- `backend/social-service/.../service/SocialService.java`: use the locked query in `block`.
- Corresponding backend and Flutter tests: prove gate exceptions, refresh-after-consent, redacted audit output, and block/accept serialization behavior.
- `docs/03-api-catalog.md` and `docs/sprint-0-1-followup.md`: document the claim, error code, client flow, test evidence, and remaining manual gates.

## Error Handling

- Missing or false `terms_accepted`: HTTP 403, `TERMS_ACCEPTANCE_REQUIRED`.
- Terms refresh failure after consent: retain authenticated credentials only if still valid, remain on Terms UI, and expose retry; do not enter the app shell.
- Concurrent accepted/blocked request: one transaction wins; the final invariant is block implies no friendship and no pending request.
- Audit failures must never fail authentication. The implementation uses local structured logging without a remote sink.

## Verification

The implementation is complete only when all of the following pass:

- Targeted identity-service tests for access-token claim and redacted audit events.
- Gateway tests showing exempt paths pass and an unaccepted user is rejected.
- Flutter tests showing Terms acceptance refreshes tokens before entering the app shell and refresh failure remains retryable.
- Social-service tests for block/accept ordering plus the existing cancel/idempotency tests.
- Full `mvn test`, Flutter analyze/tests, `docker compose config --quiet`, and `git diff --check`.
- PR #2 CI returns green for backend, Flutter, security/conventions, and database migration.

Passing these checks does not replace manual Android/iOS, OAuth, full-stack, reviewer, merge, or Trello acceptance gates.
