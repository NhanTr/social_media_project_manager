# LC-S4-03 Subscription Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. The user requested completion in this chat; execute inline.

**Goal:** Deliver the Flutter paywall, sandbox checkout reconciliation, tier management and AI entitlement gate.
**Architecture:** Typed subscription HTTP adapter over the existing authenticated ApiClient; account-scoped ChangeNotifier controller; paywall and AI screens connected to the profile. Backend dependencies remain separate and the proposed contract is clearly labeled.
**Tech Stack:** Existing Flutter/Dart, http, ChangeNotifier, url_launcher.
**Spec:** `docs/superpowers/specs/2026-11-12-s4-03-subscription-design.md`

## Global Constraints
- Initial base was origin/dev eefc3a5; final integration base is ea52c38; preserve the user's local design backup outside the repo.
- No client-granted entitlement, fabricated prices, live payment, backend ownership changes, or merge.
- Missing/unknown/stale data locks AI; all late responses respect controller disposal.
- Native external-browser checkout supports manual return and app-resume reconciliation; no success callback grants access.

## Review Focus
- Delayed webhook must leave Free locked until a fresh server grant (Task 2/3).
- Refresh errors/expiry after Plus must revoke the local AI gate (Task 2).
- Timeout/repeated checkout must reuse the idempotency request/session (Task 2).
- Account replacement during a request must drop its response (Task 2/3).
- Sandbox labels, launch failure and cancel-period behavior must be visible on small screens (Task 3).

### Task 1: Proposed contract and typed HTTP boundary
Files: `lib/features/subscription/subscription_models.dart`, `subscription_api.dart`; `test/subscription_api_test.dart`; `backend/docs/contracts/s4-mobile-subscription.{md,yaml}`.
Interfaces: SubscriptionApi.plans(), current(), checkout(clientRequestId), cancelAtPeriodEnd(), createAi(prompt), aiGeneration(id). Typed Plan, Snapshot, CheckoutSession, AiGeneration models.
- [x] Write failing tests for endpoint/payload/auth parsing, strict state validation, sandbox/HTTPS/session expiry and malformed responses.
- [x] Run `flutter test test/subscription_api_test.dart`; observe missing feature failure.
- [x] Implement typed boundary, document proposed contract, run the tests green.

### Task 2: Account-scoped controller
Files: `lib/features/subscription/subscription_controller.dart`; `test/subscription_controller_test.dart`.
Interfaces: controller.refresh(), startCheckout(), reconcileCheckout(), cancelAtPeriodEnd(), generateAi(prompt); canUseAi, snapshot, plans, busy, refreshing, paymentPending, error.
- [x] Write failing tests for checkout never granting access, delayed grant, expiry/failure, duplicate attempts, uncertain retry UUID, dispose/late responses and AI request authorization.
- [x] Run targeted tests red, implement serialization, bounded timers and state reconciliation; run green.

### Task 3: Reachable mobile flows
Files: `subscription_screen.dart`, `ai_generation_screen.dart`; shell/profile integration; widget and checkout-return tests.
Interfaces: SubscriptionScreen(controller), AiGenerationScreen(controller); shell owns/disposes the account controller and passes it to profile.
- [x] Write failing widget tests for catalog, Free/Plus, pending/failed, restore, cancellation confirmation, unavailable/retry, AI lock/unlock and app-resume delayed-webhook reconciliation.
- [x] Implement responsive list-based screens and existing AI API entry, native external launch; run targeted tests green.
- [x] Run full Flutter tests, analyze, format, build and repository policy checks. Request independent final review; fix material findings with regression tests.
- [x] Record evidence and outstanding live dependencies; commit task files and prepare a review PR linked to the card. Publication/CI are tracked in the PR; do not mark Done before live acceptance/review.
