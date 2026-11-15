# Backend closeout review — 15 November 2026

Five In Progress cards are implemented for Review & QA: LC-S4-01, LC-S4-02, LC-S2-04, LC-S4-04 and LC-S5-01. No automatic merge or Done transition. Branch: `feature/LC-S4-backend-closeout`; target: `dev` at `ea52c38`. This branch preserves the pending mobile integration commit `dc5514e` from PR #10; it does not merge or modify that PR.

## Acceptance by card

| Card | Delivered behavior | Evidence |
|---|---|---|
| LC-S4-01 | Atomic default FREE provisioning, FREE/PLUS lifecycle, effective expiry/cancellation, explicit short-lived no-store grants, audited admin override | Subscription state + PostgreSQL integration + gateway HTTP lifecycle |
| LC-S4-02 | Hosted HTTPS local sandbox, stable checkout/retry keys, raw-body HMAC verification, durable event dedup, ordering/refund/grace rules | Concurrent PostgreSQL checkout/webhooks, tampered signatures, hosted form replay, real HTTPS ingress → signed webhook → grant |
| LC-S2-04 | Provider adapter, durable leased worker/recovery/timeout, owner/input validation, internal entitlement gate, one mock charge per retried job, Post AI binding | 16 AI tests including four real PostgreSQL tests; Post authorization/AI boundary; real gateway AI job |
| LC-S4-04 | Concurrent duplicate prevention, protected queue/filter/detail, mandatory notes, immutable audit, fixed-decision downstream recovery, warning/result notification, fair retry backoff | 22 moderation tests including six disposable PostgreSQL tests; real gateway post report → hide/audit and account report → ban |
| LC-S5-01 | Safe structured logs/trace propagation, health/metrics, bounded public/internal limits, internal route denial, immediate REST/STOMP ban, account/media retention dry-run, rotation runbook | Common/gateway/chat tests, real MinIO cleanup, PostgreSQL owner cleanup, account retention exact-row dry-run equality and explicit isolated apply |

Contracts: [subscription/payment](../backend/docs/contracts/lc-s4-01-02-subscription-payment.md), [AI](../backend/docs/contracts/lc-s2-04-ai.md), [moderation](../backend/docs/contracts/lc-s4-04-moderation.md), [security/retention](../backend/docs/contracts/lc-s5-01-security.md). Updated OpenAPI: `backend/docs/contracts/s4-mobile-subscription.yaml`.

## Verification

| Check | Result |
|---|---|
| `LOCCOC_MEDIA_STORAGE_IT=true mvn -B clean verify checkstyle:check` | 11 reactor entries SUCCESS; 192 tests; 0 failures/errors/skips; zero Checkstyle violations |
| Final logging resources repackaged with `mvn -q -DskipTests package` | Exit 0; followed by full runtime acceptance below |
| `python3 scripts/check_backend_closeout.py` | PASS; new disposable PostgreSQL/Redis/MinIO, all 9 service jars, temporary TLS ingress/certificate, V1–V12 from empty, full gateway journeys |
| `flutter test --reporter compact` | 208 tests passed |
| `flutter analyze` | No issues |
| `python3 -m unittest discover -s scripts/tests -q` | 16 passed |
| Environment sample validator; Compose config; OpenAPI/workflow YAML parse; `git diff --check` | Passed |
| Independent source review | P1/P2 findings corrected; final review found no further P1/P2 in requested fixes |

[Sanitized runtime output](evidence/2026-11-15-backend-runtime.json) records the successful journey checks without tokens or credentials. The runtime script creates uniquely named temporary containers and never attaches application data volumes; its finally block terminates only its own processes/containers. Retention apply ran only against a synthetic account in that disposable database. Existing local data was not deleted.

Review/regression fixes include expired sandbox completion, actual broker SIMP MESSAGE ban enforcement, live thumbnail reference protection, normalization of real Identity access tokens without a role claim, exact public admin-login routing, stable internal-denial response, CHAR(64) identity mapping, and V12 allowing anonymized inactive/deleted tombstones while rejecting reactivation without a provider identifier. Default unused security passwords are not generated. Hibernate raw SQL constraint detail logging is disabled to avoid row/PII disclosure.

CI now enables the actual MinIO integration test and runs the full gateway/HTTPS sandbox harness after Maven verify. Published CI status is recorded on the PR and Trello cards.

## Deployment and limits

- V9 adds Subscription ownership/checkout/event/audit tables and least-privilege role; V10 adds AI lifecycle/lease/charge metadata; V11 adds moderation audit/recovery constraints; V12 enables safe identity tombstones. Existing applied migrations are unchanged.
- Configure `SANDBOX_CHECKOUT_BASE_URL` to an HTTPS ingress and a distinct >=32-character `PAYMENT_WEBHOOK_SECRET`; default local URL expects a TLS proxy on :8443. The test harness supplies its own temporary proxy. `PAYMENT_SANDBOX_ENABLED=false` disables the simulator/checkout; prod profile disables it.
- Payment is a local hosted mock, AI output is synthetic, and no external payment/AI production provider is claimed. Native Android/iOS browser/provider acceptance is not asserted by HTTP or fixture tests.
- Retention stays disabled/dry-run by default. Public media URLs issued previously are not revoked by API hiding. Cross-domain chat/social/audit/payment retention, external-hosted media, distributed rate limits and production key/provider operations retain the limits described in the runbook.
- Human review/merge and staging/release acceptance remain before Done.

## Rollback

Disable hosted sandbox and retention apply, restore the prior application artifact, and preserve payment/moderation audit history. Do not drop tables or rewrite applied migration checksums. Resolve schema changes with reviewed forward migrations and the environment backup/restore procedure; do not delete local data volumes. Previous mobile behavior fails closed when subscription endpoints are unavailable.

## Clean-runner MinIO source build

The final local/CI object-store image is `loccoc-minio:7aac2a2c5b7c`, built with `backend/Dockerfile.minio` from official MinIO commit `7aac2a2c5b7c882e68c1ce017d8256be2feea27f`. DockerHub and Quay anonymous pulls were verified unavailable; the upstream [source installation guidance](https://github.com/minio/minio#install-from-source) supports the pinned build. The image preserves source/revision/license metadata and AGPL license. Compose builds the same image and uses its HTTP health endpoint; CI builds it before real storage tests, without depending on any developer image cache.

After this change, all 51 common/Post tests passed with the source-built MinIO, and the full Gateway/HTTPS/retention runtime harness passed again. PostgreSQL readiness checks TCP on 127.0.0.1, avoiding the official image's temporary Unix-socket init server. Final clean-runner CI is tracked on PR #11; the first run already passed Flutter, security and migration but exposed the removed registry image.
