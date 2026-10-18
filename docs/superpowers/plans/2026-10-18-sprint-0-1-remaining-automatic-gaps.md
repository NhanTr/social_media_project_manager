# Sprint 0–1 Remaining Automatic Gaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the code-verifiable Terms, authentication-audit, and block/accept-concurrency gaps that keep LC-S1-01 and LC-S1-03 open.

**Architecture:** Identity issues access tokens with a `terms_accepted` claim; the API Gateway enforces that claim for user business routes while leaving auth, Terms, admin, and internal traffic usable. Flutter refreshes its session after accepting Terms. Authentication audit stays local and redacted, while social concurrency is serialized by pessimistically locking the pending friend-request row shared by `accept` and `block`.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Security, JJWT, Spring Data JPA/PostgreSQL, JUnit 5/Mockito, Flutter/Dart, flutter_test.

**Spec:** `docs/superpowers/specs/2026-10-18-sprint-0-1-remaining-automatic-gaps-design.md`

## Global Constraints

- Preserve the current public prefixes `/api/v1/**` and internal prefix `/internal/v1/**`.
- Do not log OTPs, tokens, phone numbers, email addresses, Google payloads, internal keys, or PII.
- Legacy access tokens without `terms_accepted` are treated as not accepted.
- Admin and service principals are not subject to the user Terms gate.
- Do not add Kafka, Kubernetes, service mesh, a new audit database table, or a new production dependency.
- Do not change existing Flyway migrations; add a new migration only if implementation proves one is required.
- Do not mark manual Android/iOS, OAuth, full-stack, reviewer, merge, or Trello gates complete.

## Review Focus

- Legacy user access token without the claim must receive `TERMS_ACCEPTANCE_REQUIRED`; covered by Task 2 gateway-filter tests.
- Accepted user calling an exempt Terms/auth path must not be blocked; covered by Task 2 path-matrix tests.
- Consent succeeds but token refresh fails: Flutter remains on Terms and exposes retry; covered by Task 3 controller tests.
- Audit paths must not leak supplied OTP/token/phone values; covered by Task 4 logger and AuthService tests.
- Concurrent block/accept must never leave both a block and friendship; covered by Task 5 service and PostgreSQL integration tests.

---

### Task 1: Carry Terms acceptance in the authenticated principal

**Files:**
- Modify: `backend/identity-service/src/main/java/com/nhantr/identity_service/security/JwtService.java`
- Modify: `backend/identity-service/src/main/java/com/nhantr/identity_service/service/AuthService.java`
- Modify: `backend/common-lib/src/main/java/com/nhantr/common/security/CurrentUser.java`
- Modify: `backend/common-lib/src/main/java/com/nhantr/common/security/BearerTokenFilter.java`
- Modify: `backend/identity-service/src/test/java/com/nhantr/identity_service/security/JwtServiceTests.java`
- Create: `backend/common-lib/src/test/java/com/nhantr/common/security/BearerTokenFilterTests.java`

**Interfaces:**
- Consumes: `TermsService.hasAcceptedLatest(Long userId)`.
- Produces: `JwtService.createAccessToken(Long userId, boolean termsAccepted)` and `CurrentUser(Long id, String role, boolean termsAccepted)`.

- [ ] **Step 1: Add failing JWT and bearer-filter tests** asserting true/false claim round-trip, missing-claim fallback to false, and admin/service bypass state.
- [ ] **Step 2: Run targeted tests** with `mvn -pl identity-service,common-lib -am test -Dtest=JwtServiceTests,BearerTokenFilterTests -Dsurefire.failIfNoSpecifiedTests=false`; expect failures for the missing overload/principal field.
- [ ] **Step 3: Implement the claim contract** by issuing user access tokens with `terms_accepted`, mapping absent claims to false, and preserving existing role behavior.
- [ ] **Step 4: Re-run targeted tests** and expect all new and existing tests to pass.
- [ ] **Step 5: Commit** with `git commit -m "feat(auth): LC-S1-01 carry terms acceptance in access tokens"`.

### Task 2: Enforce the Terms gate at the API Gateway

**Files:**
- Create: `backend/api-gateway/src/main/java/com/nhantr/api_gateway/filter/TermsAcceptanceFilter.java`
- Modify: `backend/api-gateway/src/main/java/com/nhantr/api_gateway/config/SecurityConfig.java`
- Create: `backend/api-gateway/src/test/java/com/nhantr/api_gateway/filter/TermsAcceptanceFilterTests.java`
- Modify: `backend/docs/03-api-catalog.md`

**Interfaces:**
- Consumes: `CurrentUser.termsAccepted()` from Task 1 and the existing `ProblemDetail` error shape.
- Produces: stable HTTP 403 response code `TERMS_ACCEPTANCE_REQUIRED` for gated user routes.

- [ ] **Step 1: Add failing filter tests** for an unaccepted user on `/api/v1/friends`, an accepted user, legacy/missing claim, `/api/v1/terms/latest`, `/api/v1/terms/accept`, `/api/v1/auth/refresh`, admin, service, health, and docs paths.
- [ ] **Step 2: Run the targeted Gateway test** with `mvn -pl api-gateway -am test -Dtest=TermsAcceptanceFilterTests -Dsurefire.failIfNoSpecifiedTests=false`; expect failure because the filter does not exist.
- [ ] **Step 3: Implement `TermsAcceptanceFilter`** as a `OncePerRequestFilter`, register it after `BearerTokenFilter`, and emit `application/problem+json` with status 403 and code `TERMS_ACCEPTANCE_REQUIRED`.
- [ ] **Step 4: Update the API catalog** with the claim, exempt paths, response code, and requirement to refresh after consent.
- [ ] **Step 5: Re-run Gateway tests** and expect all path-matrix cases to pass.
- [ ] **Step 6: Commit** with `git commit -m "feat(gateway): LC-S1-01 require current terms acceptance"`.

### Task 3: Refresh Flutter tokens after Terms consent

**Files:**
- Modify: `loccoc_application/lib/core/network/api_client.dart`
- Modify: `loccoc_application/lib/features/auth/auth_controller.dart`
- Modify: `loccoc_application/test/auth_contract_test.dart`
- Modify: `loccoc_application/test/widget_test.dart`

**Interfaces:**
- Consumes: existing `/api/v1/auth/refresh` response and `SessionStorage.updateTokens(...)`.
- Produces: `ApiClient.refreshAccessToken(): Future<bool>` and `AuthController.acceptTerms(int): Future<bool>` that enters `authenticated` only after refresh succeeds.

- [ ] **Step 1: Add failing controller tests** asserting accept→refresh→authenticated order, refresh failure remains `requiresTerms`, retry succeeds, and local `requiresTerms` is cleared only after refresh success.
- [ ] **Step 2: Run `flutter test test/auth_contract_test.dart test/widget_test.dart`** and expect the new refresh assertions to fail.
- [ ] **Step 3: Expose bounded single-flight refresh** through `ApiClient.refreshAccessToken()` without duplicating token persistence logic.
- [ ] **Step 4: Update `AuthController.acceptTerms`** to refresh first, then clear the Terms flag and load the profile; preserve the retryable error on failure.
- [ ] **Step 5: Run Flutter analyze and targeted tests** with `flutter analyze && flutter test test/auth_contract_test.dart test/widget_test.dart`; expect no issues and all tests passing.
- [ ] **Step 6: Commit** with `git commit -m "fix(flutter-auth): LC-S1-02 refresh session after terms consent"`.

### Task 4: Add redacted authentication audit events

**Files:**
- Create: `backend/identity-service/src/main/java/com/nhantr/identity_service/service/AuthAuditLogger.java`
- Modify: `backend/identity-service/src/main/java/com/nhantr/identity_service/service/AuthService.java`
- Modify: `backend/identity-service/src/main/java/com/nhantr/identity_service/service/TermsService.java`
- Create: `backend/identity-service/src/test/java/com/nhantr/identity_service/service/AuthAuditLoggerTests.java`
- Create: `backend/identity-service/src/test/java/com/nhantr/identity_service/service/AuthServiceAuditTests.java`

**Interfaces:**
- Consumes: SLF4J/MDC request ID already configured by `RequestIdFilter`.
- Produces: `AuthAuditLogger.record(String event, String outcome, Long userId)`; the method never accepts secret or PII fields.

- [ ] **Step 1: Add failing logger tests** for stable event/outcome/userId fields and absence of OTP, token, phone, email, and Google payload values.
- [ ] **Step 2: Add failing AuthService interaction tests** for OTP accepted/rate-limited, verification success/failure/attempt limit, Google verification success/failure, refresh success/rejection, logout revoked/absent, and Terms acceptance.
- [ ] **Step 3: Run targeted identity tests** with `mvn -pl identity-service -am test -Dtest=AuthAuditLoggerTests,AuthServiceAuditTests -Dsurefire.failIfNoSpecifiedTests=false`; expect failure because the audit component and calls do not exist.
- [ ] **Step 4: Implement the audit component and call sites** without allowing audit emission to change authentication outcomes.
- [ ] **Step 5: Re-run targeted tests** and expect all audit cases to pass with no sensitive values in captured output.
- [ ] **Step 6: Commit** with `git commit -m "feat(auth): LC-S1-01 add redacted authentication audit events"`.

### Task 5: Serialize block against friend-request acceptance

**Files:**
- Modify: `backend/social-service/pom.xml`
- Modify: `backend/social-service/src/main/java/com/nhantr/social_service/repository/FriendRequestRepository.java`
- Modify: `backend/social-service/src/main/java/com/nhantr/social_service/service/SocialService.java`
- Modify: `backend/social-service/src/test/java/com/nhantr/social_service/service/SocialServiceTests.java`
- Create: `backend/social-service/src/test/java/com/nhantr/social_service/service/SocialServiceConcurrencyTests.java`

**Interfaces:**
- Consumes: existing `findForUpdate(Long requestId)` lock used by `accept`.
- Produces: `findAllPairByStatusForUpdate(Long a, Long b, FriendRequest.Status status)` used by `block` before changing request/friendship state.

- [ ] **Step 1: Add failing service tests** asserting `block` uses the locked pair query, declines every pending request, removes friendship, and remains idempotent.
- [ ] **Step 2: Add a PostgreSQL-backed concurrency test** using `org.testcontainers:junit-jupiter` and `org.testcontainers:postgresql` test dependencies, `@Testcontainers`, `@Container PostgreSQLContainer<?>`, two `TransactionTemplate` calls, and latches; initialize V1 and V3 migrations, then assert either block wins and accept returns `FRIEND_REQUEST_CLOSED`, or accept commits first and block removes the friendship, with the final invariant `blocked && !friends && !pending`.
- [ ] **Step 3: Run social tests** with `mvn -pl social-service -am test`; expect failures because the locked pair query is absent.
- [ ] **Step 4: Implement the pessimistic locked pair query and use it in `block`** before deleting friendship and saving the block result.
- [ ] **Step 5: Re-run social tests** and expect cancellation, idempotency, and concurrency cases to pass.
- [ ] **Step 6: Commit** with `git commit -m "fix(social): LC-S1-03 serialize block and friend acceptance"`.

### Task 6: Update evidence and run the complete verification matrix

**Files:**
- Modify: `docs/sprint-0-1-followup.md`
- Modify: Trello descriptions only after local and remote evidence is available and the user approves the write.

**Interfaces:**
- Consumes: commits and verification output from Tasks 1–5.
- Produces: accurate remaining-gates record and a merge-readiness checklist for PR #2.

- [ ] **Step 1: Update follow-up documentation** with implemented behavior, exact test counts, and unchanged manual gates.
- [ ] **Step 2: Run backend verification**: `mvn test` from `backend/`; expect all modules successful.
- [ ] **Step 3: Run Flutter verification**: `flutter analyze && flutter test`; expect no analyzer issues and all tests passing.
- [ ] **Step 4: Run configuration and repository checks**: `docker compose config --quiet` from `backend/` and `git diff --check`; expect exit code 0.
- [ ] **Step 5: Commit evidence** with `git commit -m "docs(qa): record Sprint 0-1 automatic acceptance evidence"`.
- [ ] **Step 6: Push only after user approval**, then wait for PR #2 CI and verify all four check groups are green.
- [ ] **Step 7: Ask before Trello writes**, then update only the completed automatic acceptance bullets; keep manual gates open.
- [ ] **Step 8: Present the user with merge instructions** only after CI, review requirements, unresolved conversations, and remaining manual gates have been explicitly reconciled.
