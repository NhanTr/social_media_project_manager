# Sprint 0–1 Automatic Close-out Design

Date: 2026-10-21  
Branch: `feature/LC-S0-03-LC-S1-03-closeout`  
Base: merge commit `d3a6045` on `dev`

## 1. Objective

Close the remaining implementation-only gaps on Trello cards LC-S0-03, LC-S0-04, LC-S1-02, and LC-S1-03 without claiming manual acceptance on behalf of reviewers or testers.

Success means:

- environment configuration has explicit `dev`, `staging`, and `prod` rules and fails early when invalid;
- CI validates commit messages, builds Flutter, performs Java lint, and performs dependency vulnerability checks;
- Flutter presents reusable loading/error/retry behavior without automatically retrying unsafe mutations;
- friend search no longer performs partial matching against raw phone numbers and supports deterministic exact phone lookup;
- the PostgreSQL schema split and production provider adapters are explicitly deferred for MVP;
- each implementation item is checked in Trello only after its own commit and fresh verification evidence;
- reviewer/tester acceptance items remain unchecked until the responsible human records evidence.

## 2. Scope

### Included

1. LC-S0-03 environment and configuration validation.
2. LC-S0-04 CI convention, build, lint, and dependency gates.
3. LC-S1-02 reusable Flutter loading/error/retry presentation.
4. LC-S1-03 username and privacy-preserving exact phone search.
5. Documentation and Trello evidence for the above.

### Deferred

- PostgreSQL schema-per-service migration. MVP keeps the shared `public` schema with documented table ownership and grants.
- Production AI, payment, email, SMS, and push providers. Existing mocks/interfaces remain MVP behavior; production integrations stay in later sprint or Icebox work.
- Manual Android/iOS, OAuth, full-stack, two-account, branch-protection, reviewer, and tester acceptance.

Deferred items must be recorded as `Deferred/Accepted for MVP`, never as implemented or tested.

## 3. Environment Configuration

Flutter will expose a parsed environment value limited to `dev`, `staging`, or `prod`.

- `dev` may use the platform-local Gateway default when `API_BASE_URL` is omitted.
- `staging` requires an explicit absolute `API_BASE_URL`.
- `prod` requires an explicit HTTPS `API_BASE_URL`.
- invalid values fail before the application creates its API client.

The validation logic will be a pure Dart unit with direct tests. Compile-time defines remain the external interface: `APP_ENV`, `API_BASE_URL`, `GOOGLE_SERVER_CLIENT_ID`, and `GOOGLE_IOS_CLIENT_ID`.

Backend sample configuration will document `SPRING_PROFILES_ACTIVE=dev|staging|prod`, required secrets for non-dev environments, and matching Gateway URLs. A repository validation script will check that sample files and declared variables remain synchronized. It will not expose or generate real secrets.

## 4. CI Quality Gates

CI will keep the current four-job structure and add focused checks:

- validate each commit subject in the PR/push range against the existing Conventional Commit pattern;
- build a debug Android APK after Flutter format, analyze, and tests;
- run a conservative Java source lint configuration from the Maven root without forcing a repository-wide reformat;
- run a dependency vulnerability scan against the repository manifests/lockfiles and fail on high or critical findings that are not explicitly documented as accepted;
- run the environment/sample configuration validator.

The implementation should prefer small repository scripts plus established GitHub actions over adding application runtime dependencies. CI evidence must identify the failing commit, file, or dependency.

## 5. Flutter Loading, Error, and Retry

A reusable UI state component will represent loading, retryable failure, and content. Auth, Terms, and Friends surfaces will use the same presentation contract.

Rules:

- GET/loading operations may be retried explicitly by the user.
- POST/PUT/DELETE operations are never automatically replayed.
- mutation failures retain the current domain-specific action and display a retry affordance only when the user can safely initiate it again.
- session expiration continues to clear local session data and route to login; it is not treated as a generic retryable network error.
- error messages must not contain token, OTP, internal key, or raw response payload data.

Widget/controller tests will cover loading, visible failure, explicit retry, and no automatic replay of a mutation.

## 6. Username and Phone Lookup

Username and display-name search remain case-insensitive partial matches. Raw phone-number `LIKE` search will be removed.

Phone lookup behavior:

- normalize the submitted phone value to the project's canonical E.164-like representation;
- compute a deterministic SHA-256 lookup hash;
- compare the hash exactly, never partially;
- store the lookup hash in a new nullable, unique, indexed column added through a new Flyway migration;
- populate/update the hash whenever a phone-backed user is created or the phone value changes;
- backfill existing phone-backed users in the migration using PostgreSQL `pgcrypto` and the same normalization/hash algorithm;
- never return the phone lookup hash through public or internal DTOs.

Tests will cover normalization, deterministic hashing, username partial search, exact phone-hash search, and the absence of raw phone partial matching.

## 7. Verification and Trello Policy

Each implementation unit follows red-green-refactor and receives its own commit. After a commit:

1. run the focused tests for that unit;
2. run the relevant broader suite and formatting/diff checks;
3. update only the matching Trello implementation item when evidence is green;
4. attach the commit SHA and test evidence to the card;
5. leave all reviewer/tester items unchanged.

Expected Trello mapping:

- environment/config commit → LC-S0-03 implementation items;
- CI gates commit → LC-S0-04 implementation items;
- Flutter state/retry commit → LC-S1-02 implementation item;
- phone lookup commit → LC-S1-03 implementation item;
- deferral documentation commit → comments/description on LC-S0-02, without checking the two deferred items as implemented.

Cards remain in `04 Review & QA` until their human acceptance gates pass.

## 8. Rollback and Compatibility

- Every change is isolated in a separate commit and can be reverted through a reviewed PR.
- The phone-hash migration is additive; rollback code may leave the column and extension in place. Applied migrations must not be edited or removed.
- Existing username search behavior remains compatible.
- CI additions do not change application runtime behavior.
- Environment validation keeps current local development defaults for `dev`.

