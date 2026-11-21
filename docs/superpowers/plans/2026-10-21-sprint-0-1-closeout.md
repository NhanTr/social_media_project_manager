# Sprint 0–1 Automatic Close-out Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the remaining implementation-only Sprint 0–1 gaps, verify each independently, and update only the matching Trello implementation checklist after each commit.

**Architecture:** Add small, independently testable validation units around the existing Flutter, Maven, and GitHub Actions structure. Preserve current public contracts except for replacing raw partial phone search with exact normalized-hash lookup. Keep schema-per-service and production providers explicitly deferred.

**Tech Stack:** Dart/Flutter 3.47.5, Java 21/Spring Boot/Maven, PostgreSQL 16/Flyway, Python 3 standard library, GitHub Actions, Trello.

**Spec:** `docs/superpowers/specs/2026-10-21-sprint-0-1-closeout-design.md`

## Global Constraints

- Work from `feature/LC-S0-03-LC-S1-03-closeout`, based on merge commit `d3a6045` from `dev`.
- Use TDD for behavior changes: observe the focused test fail for the intended reason before changing production code.
- Do not edit an applied Flyway migration; phone lookup uses a new V4 migration.
- Do not automatically retry POST, PUT, or DELETE requests.
- Do not expose phone lookup hashes, tokens, OTPs, internal keys, or raw sensitive payloads.
- Do not check reviewer/tester Trello items or move cards out of `04 Review & QA`.
- After every implementation commit: run focused verification, push the commit, add Trello evidence, and check only the mapped implementation item.
- PostgreSQL schema-per-service and production AI/payment/email/push providers remain deferred for MVP.

## Review Focus

1. Unknown or empty `APP_ENV` must fail deterministically; `dev` alone may omit `API_BASE_URL`.
2. Conventional-commit validation must handle merge commits and the exact PR/push revision range without accepting an empty subject.
3. Retry UI must never replay a mutation automatically or convert session expiration into a generic retry.
4. Phone normalization must produce the same hash in Java and V4 SQL for `090...`, `84...`, and `+84...` forms.
5. Raw partial phone input must not return a match; only exact normalized phone lookup may match.

---

### Task 1: Validate dev, staging, and production configuration

**Files:**
- Create: `loccoc_application/lib/core/config/environment_config.dart`
- Create: `loccoc_application/test/environment_config_test.dart`
- Modify: `loccoc_application/lib/core/config/app_config.dart`
- Create: `scripts/validate_environment_config.py`
- Create: `scripts/tests/test_validate_environment_config.py`
- Modify: `backend/.env.example`
- Create: `backend/.env.staging.example`
- Create: `backend/.env.prod.example`
- Modify: `loccoc_application/README.md`

**Interfaces:**
- Produces Dart `enum AppEnvironment { dev, staging, prod }`.
- Produces `EnvironmentConfig.parse({required String environment, required String apiBaseUrl})` returning a validated immutable configuration.
- Produces CLI `python3 scripts/validate_environment_config.py <env-file>...`, exit 0 only when required keys and safe placeholder rules pass.

- [ ] **Step 1: Write failing Dart environment tests**

Add tests for dev default URL, staging missing URL, production HTTP URL, valid production HTTPS URL, and unknown/empty environment.

- [ ] **Step 2: Verify Dart tests fail because the validation unit does not exist**

Run: `flutter test test/environment_config_test.dart --timeout 30s` from `loccoc_application/`.

- [ ] **Step 3: Implement the Dart configuration unit and wire `AppConfig` to it**

Keep compile-time defines as the public input. `dev` uses the existing platform default; `staging` requires an absolute URL; `prod` requires HTTPS.

- [ ] **Step 4: Write failing Python tests for sample environment validation**

Use `unittest` temporary files to assert missing `SPRING_PROFILES_ACTIVE`, missing non-dev secrets, real-looking secrets, and the three valid sample profiles.

- [ ] **Step 5: Verify Python tests fail because the validator does not exist**

Run: `python3 -m unittest scripts.tests.test_validate_environment_config -v`.

- [ ] **Step 6: Implement the validator and sample environment files**

Require `SPRING_PROFILES_ACTIVE`, `JWT_SECRET`, `INTERNAL_API_KEY`, database values, and public Gateway URL for staging/prod. Samples contain placeholders only.

- [ ] **Step 7: Verify Task 1**

Run:

```bash
python3 -m unittest scripts.tests.test_validate_environment_config -v
python3 scripts/validate_environment_config.py backend/.env.example backend/.env.staging.example backend/.env.prod.example
cd loccoc_application && dart format --output=none --set-exit-if-changed lib test
cd loccoc_application && flutter analyze
cd loccoc_application && flutter test test/environment_config_test.dart --timeout 30s
git diff --check
```

Expected: every command exits 0.

- [ ] **Step 8: Commit, push, and update Trello**

Commit: `feat(config): LC-S0-03 validate deployment environments`

Push the branch, comment on LC-S0-03 with SHA and commands, then check:

- `Triển khai: Environment dev staging prod`
- `Triển khai: Config validation và sample env`

Do not check local-stack, device, or Gateway-health acceptance.

---

### Task 2: Add missing CI quality gates

**Files:**
- Create: `scripts/check_conventional_commits.py`
- Create: `scripts/tests/test_check_conventional_commits.py`
- Create: `scripts/tests/test_ci_contract.py`
- Create: `backend/config/checkstyle/checkstyle.xml`
- Modify: `backend/pom.xml`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`

**Interfaces:**
- Produces CLI `python3 scripts/check_conventional_commits.py <base> <head>`.
- Maven root exposes `mvn -B -DskipTests checkstyle:check` and keeps `mvn -B clean verify` as the unit/build gate.
- CI contract includes environment validation, commit-subject validation, Flutter debug APK build, Java lint, and dependency scanning.

- [ ] **Step 1: Write failing commit-validator tests**

Test valid conventional subjects, invalid/missing type, empty subject, breaking-change marker, and merge-commit handling using temporary Git repositories.

- [ ] **Step 2: Verify commit-validator tests fail**

Run: `python3 -m unittest scripts.tests.test_check_conventional_commits -v`.

- [ ] **Step 3: Implement the commit validator**

Use Git revision ranges supplied by CI. Report every invalid SHA and subject; ignore only standard `Merge ...` commits.

- [ ] **Step 4: Write the failing CI contract test**

Assert the workflow contains commands/actions for the environment validator, commit validator, `flutter build apk --debug`, Maven Checkstyle, Maven verify, and a high/critical dependency vulnerability gate.

- [ ] **Step 5: Verify the CI contract test fails on the current workflow**

Run: `python3 -m unittest scripts.tests.test_ci_contract -v`.

- [ ] **Step 6: Add focused Checkstyle and CI steps**

Configure Checkstyle with exactly `AvoidStarImport`, `FileTabCharacter`, `EmptyStatement`, and a Java trailing-whitespace `RegexpSinglelineJava` rule. Add `aquasecurity/trivy-action@0.29.0` in filesystem mode with `severity: HIGH,CRITICAL`, `ignore-unfixed: true`, and `exit-code: 1`. For commit validation, use `${{ github.event.pull_request.base.sha }}..${{ github.event.pull_request.head.sha }}` on pull requests and `${{ github.event.before }}..${{ github.sha }}` on pushes.

- [ ] **Step 7: Verify Task 2 locally**

Run:

```bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_conventional_commits.py origin/dev HEAD
cd backend && mvn -B -DskipTests checkstyle:check
cd backend && mvn -B test
cd loccoc_application && flutter analyze
cd loccoc_application && flutter test --timeout 30s
cd loccoc_application && flutter build apk --debug --dart-define=APP_ENV=dev
git diff --check
```

Expected: all commands exit 0. If the host JDK cannot run Mockito, repeat Maven verification with JDK 21 in the project Docker/CI environment and record both the host limitation and the passing JDK 21 evidence.

- [ ] **Step 8: Commit, push, wait for CI, and update Trello**

Commit: `ci: LC-S0-04 complete quality gates`

Open or update the PR so GitHub Actions runs. Only after the new run is green, comment on LC-S0-04 with SHA/run URL and check:

- `Triển khai: Conventional commit check`
- `Triển khai: Flutter analyze, test và build`
- `Triển khai: Backend lint và unit test`
- `Triển khai bổ sung: Chạy secret scan và dependency audit cơ bản`

Do not check either intentional-red-PR item; those remain reviewer/tester evidence.

---

### Task 3: Introduce reusable Flutter loading, error, and retry UI

**Files:**
- Create: `loccoc_application/lib/core/widgets/retryable_async_view.dart`
- Create: `loccoc_application/test/retryable_async_view_test.dart`
- Modify: `loccoc_application/lib/features/auth/screens/login_screen.dart`
- Modify: `loccoc_application/lib/features/auth/screens/terms_screen.dart`
- Modify: `loccoc_application/lib/features/friends/screens/friends_screen.dart`
- Modify: `loccoc_application/test/widget_test.dart`
- Modify: `loccoc_application/test/social_flow_test.dart`

**Interfaces:**
- Produces widget `RetryableAsyncView({required bool isLoading, String? errorMessage, VoidCallback? onRetry, required Widget child})`.
- Retry callbacks invoke existing controller load/login/terms actions only after a user tap.

- [ ] **Step 1: Write failing widget tests**

Test the loading state, visible sanitized error, retry button callback exactly once, content state, and absence of a retry button when no safe callback is supplied.

- [ ] **Step 2: Verify widget tests fail because the widget does not exist**

Run: `flutter test test/retryable_async_view_test.dart --timeout 30s`.

- [ ] **Step 3: Implement the reusable widget and integrate read/loading surfaces**

Use the existing controllers and preserve existing copy where possible. Never invoke `onRetry` during build or automatically after failure.

- [ ] **Step 4: Add controller/widget regression tests for mutation safety**

Assert a failed send/cancel/block mutation is not replayed until the user invokes that action again, and session expiration still routes to login.

- [ ] **Step 5: Verify Task 3**

Run:

```bash
cd loccoc_application && dart format --output=none --set-exit-if-changed lib test
cd loccoc_application && flutter analyze
cd loccoc_application && flutter test --timeout 30s
git diff --check
```

Expected: format check, analyzer, and all Flutter tests pass.

- [ ] **Step 6: Commit, push, and update Trello**

Commit: `feat(flutter): LC-S1-02 unify loading error and retry states`

Push, comment on LC-S1-02 with SHA/test counts, then check `Triển khai: Global loading/error/retry`. Leave cold-start, 401, logout, token-log, device, and OAuth acceptance untouched.

---

### Task 4: Replace raw partial phone search with exact hashed lookup

**Files:**
- Create: `backend/database/migration/V4__phone_lookup_hash.sql`
- Create: `backend/identity-service/src/main/java/com/nhantr/identity_service/security/PhoneLookupHasher.java`
- Create: `backend/identity-service/src/test/java/com/nhantr/identity_service/security/PhoneLookupHasherTests.java`
- Create: `backend/identity-service/src/test/java/com/nhantr/identity_service/service/UserSearchTests.java`
- Modify: `backend/identity-service/src/main/java/com/nhantr/identity_service/entity/User.java`
- Modify: `backend/identity-service/src/main/java/com/nhantr/identity_service/repository/UserRepository.java`
- Modify: `backend/identity-service/src/main/java/com/nhantr/identity_service/service/UserService.java`
- Modify: `backend/identity-service/src/main/java/com/nhantr/identity_service/service/AuthService.java`
- Modify: `backend/identity-service/src/main/java/com/nhantr/identity_service/controller/InternalUserController.java`
- Modify: `backend/identity-service/src/main/java/com/nhantr/identity_service/config/InitUserTestConfig.java`
- Modify: `backend/docs/03-api-catalog.md`

**Interfaces:**
- Produces `PhoneLookupHasher.normalize(String raw): String` and `PhoneLookupHasher.hash(String raw): String` using SHA-256 lowercase hex.
- Produces `UserService.search(String keyword): List<UserResponse>`.
- Repository separates partial username/display-name search from exact `phoneLookupHash` lookup.

- [ ] **Step 1: Write failing phone normalization/hash tests**

Cover `0901234567`, `84901234567`, and `+84 901 234 567` producing one canonical/hash value; reject blank, alphabetic, too-short, and partial phone input.

- [ ] **Step 2: Verify hasher tests fail because the class does not exist**

Run: `mvn -B -pl identity-service -am -Dtest=PhoneLookupHasherTests -Dsurefire.failIfNoSpecifiedTests=false test` from `backend/` using JDK 21.

- [ ] **Step 3: Implement the pure hasher**

Canonicalize Vietnamese local `0...` and country-code `84...` into `+84...`; accept already-prefixed `+...`; hash canonical UTF-8 bytes with SHA-256.

- [ ] **Step 4: Write failing search-routing tests**

Assert textual input uses username/display-name partial search, exact phone input uses a hash equality query, and partial phone input does not fall back to raw `LIKE` search.

- [ ] **Step 5: Verify search tests fail on the current repository/controller flow**

Run the focused `UserSearchTests` with JDK 21.

- [ ] **Step 6: Add V4, entity field, repository methods, and service routing**

V4 enables `pgcrypto`, adds `phone_lookup_hash CHAR(64)`, backfills with the SQL equivalent of the canonicalization algorithm, adds a unique partial index for non-null values, and never changes `phone_number` itself. New/updated phone-backed users receive the hash before persistence.

- [ ] **Step 7: Verify migration and backend behavior**

Run:

```bash
cd backend && mvn -B -pl identity-service -am test
cd backend && mvn -B test
cd backend && docker compose config --quiet
```

Run Flyway `migrate validate` against an empty PostgreSQL 16 test database and query that existing fixture phone values have 64-character hashes. Expected: all tests and Flyway operations exit 0; no DTO contains `phoneLookupHash`.

- [ ] **Step 8: Commit, push, and update Trello**

Commit: `feat(user): LC-S1-03 add hashed phone lookup`

Push, comment on LC-S1-03 with SHA/test/migration evidence, then check `Triển khai: Search username/phone hash`. Leave all social-flow acceptance items untouched.

---

### Task 5: Record MVP deferrals and final branch evidence

**Files:**
- Modify: `docs/architecture/01-mvp-boundaries-and-architecture.md`
- Modify: `docs/sprint-0-1-followup.md`
- Modify: `backend/docs/05-gaps-and-roadmap.md`
- Modify: `README.md`

**Interfaces:**
- Produces one consistent statement: shared `public` schema plus ownership/grants is accepted for MVP; schema-per-service and production providers are deferred.

- [ ] **Step 1: Add a failing documentation contract test**

Extend `scripts/tests/test_ci_contract.py` or add `scripts/tests/test_closeout_docs.py` to assert the four documents contain the same deferral status and do not call mocks production-ready.

- [ ] **Step 2: Verify the documentation test fails**

Run: `python3 -m unittest scripts.tests.test_closeout_docs -v`.

- [ ] **Step 3: Update architecture, roadmap, follow-up, and root README**

Record the accepted MVP state, later destination, reason, and non-production limitations. Do not mark the Trello implementation items for schema split or production adapters complete.

- [ ] **Step 4: Run complete verification**

Run:

```bash
python3 -m unittest discover -s scripts/tests -v
cd backend && mvn -B clean verify
cd backend && mvn -B -DskipTests checkstyle:check
cd backend && docker compose config --quiet
cd loccoc_application && dart format --output=none --set-exit-if-changed lib test
cd loccoc_application && flutter analyze
cd loccoc_application && flutter test --coverage --timeout 30s
cd loccoc_application && flutter build apk --debug --dart-define=APP_ENV=dev
git diff --check
```

Use JDK 21 for Maven. Record exact test counts, migration result, and artifact path.

- [ ] **Step 5: Commit, push, and update Trello without false completion**

Commit: `docs: record Sprint 0-1 MVP deferrals and evidence`

Push and add comments to LC-S0-02 explaining:

- `Một PostgreSQL cluster tách schema` → deferred; MVP uses shared `public` schema with ownership/grants.
- `Adapter AI, payment, email, push` → deferred to later sprint/Icebox; mocks are not production adapters.

Do not check those two implementation items. Add final evidence comments to LC-S0-03/04 and LC-S1-02/03. Keep all cards in `04 Review & QA`.

- [ ] **Step 6: Open the pull request**

Open a PR from `feature/LC-S0-03-LC-S1-03-closeout` to `dev`, link the four affected Trello cards, include verification results, rollback notes, and the two explicit deferrals. Attach the PR to this Codex task. Do not merge it.

