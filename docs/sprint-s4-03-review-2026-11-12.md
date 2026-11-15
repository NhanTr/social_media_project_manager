# LC-S4-03 implementation and acceptance evidence — 12 November 2026

Card: https://trello.com/c/ESGenxaf. Task branch: `feature/LC-S4-03-flutter-subscription`, targeting `dev`.

## Delivered

Profile now opens a Free/Plus comparison and tier management screen. The app uses authenticated server catalog/snapshot responses, displays sandbox prices, opens HTTPS sandbox checkout with `url_launcher`, refreshes on return/resume and manually, shows pending/failed payment, confirms cancel-at-period-end, and gates the existing trial AI generation API on a fresh explicit entitlement. No tier is granted by checkout launch or callback. The account-scoped controller discards late results on logout/account replacement.

Uncertain checkout retries retain the same UUID; known unexpired sessions are reused. Payment results are correlated by session. Explicit HTTP 410 expiration permits a new request; unrelated old terminal snapshots cannot erase an unresolved request identity. Reconciliation stops after four follow-up polls and retains a manual refresh control.

## Remote synchronization and preservation

Fetched all remote refs and fast-forwarded local `dev`. The initial base was `eefc3a5`; while implementing, PRs #6, #8, #9 and #7 merged into remote `dev`. Rebased this task onto `ea52c38`, retaining the incoming S3 chat/notification shell behavior and subscription integration. The account-switch test follows the incoming shell's return-to-feed behavior. No shared branch was merged/pushed by this task.

The pre-existing untracked S2 design differed from the tracked remote file and blocked the first pull. Its original contents were preserved outside the repository at `../preserved-local-files/2026-10-25-sprint-s2-02-03-design.md` before pulling.

## Verification

| Check | Observed result |
| --- | --- |
| Full Flutter suite, final combined base | **208 tests passed**, including 40 subscription tests |
| `flutter analyze` | No issues found |
| Dart format | No changes required for lib/test/task integration test |
| Android debug APK | Build succeeded after latest remote merges |
| iPhone 17 / iOS 27.0 | Xcode build succeeded; **1 native fixture acceptance test passed** |
| Native visual inspection | Plus, sandbox price, cancellation/period-end and scrolling inspected; screenshot saved outside Git at `../deliverables/LC-S4-03/paywall-plus-ios.png` |
| `mvn -B clean verify`, final combined base | All 10 reactor modules succeeded; 99 tests, 0 failures/errors, **10 skipped** |
| Repository policy tests | 16 passed |
| Docker Compose configuration | Valid |
| Proposed OpenAPI | YAML parsed; four subscription paths |
| Git whitespace | `git diff --check` clean |

The Maven skips are existing Docker/PostgreSQL-dependent tests; skipped checks are not passing integration evidence. The native acceptance test uses real Flutter rendering/controller/API with fixture HTTP transport and an injected browser-launch boundary; it does **not** demonstrate a real provider browser checkout or webhook.

Independent final review found two P2 retry issues (unrelated old terminal snapshot erasing uncertain UUID; HTTP 410 repeatedly using an expired UUID). Both were reproduced with failing regression tests, fixed, and verified in the full suite. No reviewer minor findings were deferred.

## Acceptance boundary and dependencies

LC-S4-01 Subscription and LC-S4-02 Payment are still In Progress and absent from fetched refs at the final integration base. The [mobile contract](../backend/docs/contracts/s4-mobile-subscription.md) and [OpenAPI](../backend/docs/contracts/s4-mobile-subscription.yaml) are explicitly **proposed**, not implemented backend endpoints. Missing services show a retryable unavailable screen and keep AI locked; no demo subscription adapter is enabled in production code.

Before Done:

- Backend owners implement/align catalog, snapshot, checkout, cancellation and Gateway routes.
- Verify real Android/iOS browser launch/return and account restart with a sandbox provider, delayed/failed/replayed/out-of-order webhooks and period-end expiry.
- Enforce entitlements in AI/Post service boundaries. The existing AI provider is still a mock; UI gating alone is not paid-feature authorization.
- Confirm CI on the PR and reviewer/QA acceptance. No production payment, store billing restoration, or production AI delivery is claimed.

Implementation is ready for **Review & QA**, with live dependency acceptance pending. Do not mark the card Done or merge automatically.

## Rollback

Revert the task commits to remove the profile entries, subscription flow and URL-launcher dependency. This task changes no backend runtime, schema, migrations or payment provider state.
