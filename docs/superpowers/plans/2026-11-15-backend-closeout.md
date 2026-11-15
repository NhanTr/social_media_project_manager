# Remaining In Progress cards implementation plan

> **For agentic workers:** Implement the independent domains with superpowers:dispatching-parallel-agents and test-driven-development. Integrate and verify the whole branch before moving cards.

**Goal:** Deliver LC-S4-01, LC-S4-02, LC-S2-04, LC-S4-04 and LC-S5-01 to Review & QA with executable evidence and a PR.

**Architecture:** Keep Java 21 / Spring Boot 4.1, MVC Gateway, PostgreSQL ownership and existing public contracts. Add subscription-service :8088 owning tier, checkout and webhook records; sandbox payment is a local deterministic adapter, not a production provider. AI reads grants through authenticated internal REST and never reads subscription tables. Moderation and security extend their existing owners.

**Spec:** The five Trello card descriptions in Project Management, and backend/docs/contracts/s4-mobile-subscription.md (mobile contract). User confirmed completion of the five In Progress cards after the proposed dependency order.

## Constraints and decisions
- Work on feature/LC-S4-backend-closeout from dc5514e, retaining the reviewed mobile contract and its unmerged UI dependency. No merge into dev.
- Migration allocation: V9 subscription/payment (primary), V10 AI (AI worker), V11 moderation (moderation worker), V12 retention if needed (security worker). Never edit applied migrations.
- Primary owns root pom, Compose, gateway application.yaml/security webhook allowlist and top-level docs/integration harness. Domain workers must not edit these shared files.
- Internal subscription contract: GET /internal/v1/subscriptions/{userId}/entitlements with X-Internal-Api-Key returns {aiGeneration:boolean,validUntil:ISO instant}; missing/unavailable/expired denies AI. X-Request-Id propagates.
- Public subscription paths exactly follow the S4 mobile contract; private endpoints enforce user principal and Terms. Admin override requires admin role and an audit note.
- Payment sandbox hosts a local HTTPS-configurable checkout URL; only raw-body HMAC-SHA256 signed webhook events can grant tier. No client success callback grants anything. Fixtures/adapter tests are explicitly distinguished from external-provider evidence.
- Review & QA requires a PR and passing checks. No automatic merge or mark Done.

## Review focus
1. Concurrent/replayed checkout and webhook requests must not double-grant or replace a newer payment.
2. Entitlement expiry/cancellation and unavailable subscription must fail closed server-side.
3. Moderation downstream failure/retry must preserve an honest audit and avoid duplicate user effects.
4. Retention dry-run must not mutate data or storage; active/consumed media must survive cleanup.
5. Logs/request IDs/rate-limit identities must not expose secrets or trust spoofed headers.

## Task 1 — LC-S4-01 Subscription
Files: backend/subscription-service/pom.xml, src/main/java/com/nhantr/subscription_service/**, src/test/**; database/migration/V9__subscription_payment.sql; root pom and Compose.
- [x] Write lifecycle tests: default FREE; one account state; PLUS expiry; cancel at end; audited admin override; effective time; concurrent updates.
- [x] Observe missing-feature failure, implement persisted service with database locks and unique account key.
- [x] Expose plans/me/cancel/admin override and internal entitlement, match mobile JSON/no-store and validate principals.
- [x] Run module tests and add migration/runtime integration evidence.

## Task 2 — LC-S4-02 Payment
Files: subscription-service payment adapter/service/controllers/tests, V9; gateway route/public webhook allowlist.
- [x] Write tests for same-key replay/conflict, multiple keys sharing pending session, invalid signature, duplicate event, older event, failure/grace/refund and expired session.
- [x] Implement durable checkout keys/events with account serialization, raw-body HMAC verification, bounded timestamp and ordered transitions. Persist event and tier change together.
- [x] Add sandbox checkout page/harness, configured HTTPS base URL; browser view and return cannot write tier.
- [x] Run payment suite and signed webhook → entitlement integration through gateway.

## Task 3 — LC-S2-04 AI
Files: ai-service/**, relevant Post AI-reference validation if necessary, V10, docs/contracts/lc-s2-04-ai.md.
- [x] Write failing state/owner/input/grant/retry/timeout tests; implement deterministic provider adapter, durable after-commit worker and recovery; no additional mock charge on retry.
- [x] Enforce internal entitlement before create/retry; timeout/failure preserves original-media fallback and ordinary posting.
- [x] Run module suite and report exact commands/results/limitations.

## Task 4 — LC-S4-04 Moderation
Files: moderation-service/**, minimal identity/post internal enforcement, V11, docs/contracts/lc-s4-04-moderation.md.
- [x] Write failing duplicate-report, admin authorization, queue/filter, note/audit and downstream failure/replay tests.
- [x] Implement immutable audit and transactional locking, actions HIDE_POST/WARN_USER/BAN_USER/REJECT, idempotent downstream calls and result notification.
- [x] Verify hidden/banned effects and rollback/recovery behavior; run tests and report evidence.

## Task 5 — LC-S5-01 Security/retention
Files: common-lib/**, gateway filters except shared configuration above, identity deletion worker, post cleanup, relevant tests, V12 if needed; docs/contracts/lc-s5-01-security.md.
- [x] Write failing sanitized request-ID propagation, public/internal rate limit, secret/PII logging and retention dry-run tests.
- [x] Add safe structured request telemetry/basic metrics, bounded local MVP limits, internal gateway denial and rotation runbook.
- [x] Implement bounded account/media retention with explicit dry-run and domain ownership; preserve data unless its documented policy is due.
- [x] Run focused tests and policy/security smoke; report limits honestly (single-instance limiter, mock adapters).

## Task 6 — Integrated review and publication
- [x] Run full Maven clean verify/checkstyle, repository policy suite, Compose validation, migration on empty DB and full runtime health/critical HTTP smoke where infrastructure is available.
- [x] Run Flutter subscription tests/analyze against the preserved client contract.
- [x] Review combined changes; fix material findings with regression tests; record acceptance per card and any external dependency.
- [x] Commit/push feature branch and open PR #11 linked to all five cards.
- CI confirmation and final Review & QA moves are tracked on PR #11 and the live Trello board; no automatic merge or Done transition.

## Execution ledger
- 2026-11-15: Ready empty; user explicitly confirmed the five In Progress cards. Existing UI PR #10 is draft and remains untouched. GitHub connector confirmed no subscription branch. Shell git fetch is network-restricted; connector read access works. Baseline Maven tests started before edits.

- Final local evidence: 192 backend tests with zero failures/errors/skips, 208 Flutter tests, 16 policy tests, full isolated Gateway/HTTPS sandbox + retention PASS. V12 was required by the actual tombstone constraint regression. Full runtime exposed and now tests real JWT role normalization, Identity CHAR mapping and Gateway admin/internal routing. No P1/P2 remains from independent review. PR #11 is published; CI confirmation/card moves are tracked on the PR and live board.

- Clean-runner follow-up: DockerHub/Quay MinIO anonymous pulls failed. Added an official pinned-source MinIO image build used by Compose, Testcontainers and runtime harness. Source build, 51 common/Post tests and full Gateway/HTTPS/retention harness all passed; revised CI builds the image before acceptance.
