# LC-S2-04 — deterministic AI generation MVP

Public mobile API and JSON names are unchanged:

| Method | Path | Result |
| --- | --- | --- |
| POST | `/api/v1/ai/generations` | 202 with persisted `PENDING` job |
| GET | `/api/v1/ai/generations/{id}` | Owner's current job |
| POST | `/api/v1/ai/generations/{id}/retry` | 202; only owner of `FAILED` job |

Create body:

```json
{"originalMediaUrl":"https://media.example/original.png","stylePrompt":"anime","generationType":"IMAGE_TO_IMAGE"}
```

Types: `TEXT_TO_IMAGE`, `IMAGE_TO_IMAGE`, `FILTER_ENHANCE`. Prompt is trimmed,
nonblank, at most 1000 characters. Image/filter modes require an original URL;
provided URLs must be absolute HTTP(S), at most 2048 characters, without embedded
credentials or fragments. The fake provider does not download the source.
URL ownership/storage provenance is not established by this API; production
providers must validate owned media rather than fetching arbitrary URLs.

Response fields remain `id`, `userId`, `originalMediaUrl`, `stylePrompt`,
`generatedMediaUrl`, `generationType`, `status`, `errorMessage`, `createdAt`,
`completedAt`. Failed jobs retain `originalMediaUrl`, return null generated media,
and have a stable `AI_PROVIDER_FAILED`, `AI_PROVIDER_TIMEOUT`, or
`AI_PROVIDER_INTERRUPTED` error message. Clients can post the original through
ordinary posting (`aiGenerated=false`, no `aiGenerationId`) independently of AI
availability or subscription status.

## Authorization and failure policy

Create and retry synchronously call
`GET /internal/v1/subscriptions/{userId}/entitlements` using
`X-Internal-Api-Key` and the sanitized ingress `X-Request-Id`. Both
`aiGeneration=true` and a strictly future `validUntil` are required. Missing,
expired, malformed or unavailable entitlement denies with 403 `AI_TIER_REQUIRED`.
No local grant cache or subscription-table access exists. Connect/read timeout is
2 seconds. GET remains available to the owner after expiry. Foreign/missing IDs
return 404 `AI_GENERATION_NOT_FOUND`; retry of another state returns 409
`AI_RETRY_NOT_ALLOWED`.

Subscription URL: `SUBSCRIPTION_SERVICE_URL` (default `http://localhost:8088`).

## Durable worker and deterministic adapter

`PENDING -> PROCESSING -> SUCCESS/FAILED` is persisted in `ai_generations`.
Database polling claims committed rows using `FOR UPDATE SKIP LOCKED` in a short
transaction. Provider execution begins after that transaction commits. No task
is scheduled from the create/retry transaction, so rollback cannot publish work.
A fresh process finds pending jobs and expired processing leases. Attempt tokens
fence late completions, so old calls cannot replace a recovered result.

Configuration: `AI_WORKER_POLL_MS=500`, `AI_PROVIDER_TIMEOUT_MS=5000`,
`AI_WORKER_LEASE_SECONDS=30`. Startup rejects nonpositive timeout or lease shorter
than the timeout. One bounded provider thread per process prevents unbounded
orphan calls; timeout interrupts the provider future. This MVP can run several
instances sharing PostgreSQL; locks prevent simultaneous claims. A provider that
ignores interruption can stall that process's single provider thread, causing
later jobs to fail until restart. Lease recovery permits another instance to
continue.

The adapter performs no AI request and no monetary charge. Image output is the
original URL with a stable `aiGeneration={jobId}` query marker; text output is a
stable Picsum placeholder URL. The adapter does not transform or upload pixels.
Prompt markers `[fail]` and `[timeout]` exercise failure and bounded timeout.
Migration V10 persists attempt count and a per-job mock charge marker (`0` or `1`);
claim/recovery/retry can never increment it above one. Retry reuses the job ID.
Repeated create calls intentionally create new jobs; clients must retry a failed
job through its retry endpoint. Real payment/quota accounting and production AI,
safety, source validation and cancellation remain deferred.

## Post reference boundary

`GET /internal/v1/ai/generations/{id}?userId={owner}` exposes the same generation
response exclusively to authenticated internal services, never through Gateway.
Post validates both `aiGenerated=true` and a nonnull `aiGenerationId`, then calls
AI and requires matching job ID, authenticated post owner, `SUCCESS` and exact
`generatedMediaUrl`. Unavailable/malformed/foreign/unfinished/mismatching jobs
fail closed as 400 `AI_REFERENCE_INVALID`. Missing flag/reference pairing returns
400 `AI_REFERENCE_REQUIRED`. Existing publication-key replay returns the original
post without contacting AI again. Ordinary posting never calls AI. Post client
uses `clients.ai.base-url` (`AI_SERVICE_URL`, default `http://localhost:8086`),
internal authentication, propagated request ID and two-second timeouts.

## Evidence

AI tests cover grant denial, owner isolation, input validation, retry rules,
retained charge, provider success/failure/timeout, token fencing and live HTTP
entitlement contract/headers. PostgreSQL Testcontainers cover V10 and Hibernate
validation, pending work after commit, expired lease recovery, rollback isolation,
concurrent claim and timeout/retry persistence. Post tests cover full generation
JSON decoding, owner/status/URL mismatch, flag/reference mismatch and ordinary
fallback posting. These tests prove the deterministic local adapter, not an
external AI provider or generated-pixel quality.
