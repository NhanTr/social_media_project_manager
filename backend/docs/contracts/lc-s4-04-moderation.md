# LC-S4-04 Moderation

## Authentication and compatibility

`POST /api/v1/reports` requires a USER JWT. Every admin read/write requires a JWT with `token_type=admin` and role MODERATOR or SUPER_ADMIN at the moderation service. An access token carrying an admin role still receives 403. Internal APIs require the internal service key and are not public routes.

The existing list (`GET /api/v1/admin/reports`, JSON array), detail, login, resolve and reject paths remain available. Resolve/reject now require a nonblank `resolutionNote` of at most 2000 characters. Clients should use the new bounded queue for browsing.

## Reports and queue

Create: `{reportedPostId:123, reason:"Spam content"}` or `{reportedUserId:456, reason:"Spam account"}`. Exactly one target is required; reporting oneself is rejected. Service owners verify target existence. One reporter can submit only one report per target, including after closure. Duplicate requests return 409 `REPORT_DUPLICATE`; PostgreSQL partial unique indexes enforce this under concurrency.

`GET /api/v1/admin/reports/queue?status=PENDING&limit=20&cursor=...` returns `PageResponse<ReportResponse>`: `{items:[],nextCursor:null,hasNext:false}`. Status is optional; values are PENDING, INVESTIGATING, RESOLVED, REJECTED. Limit is 1–100. Results use descending immutable report IDs. Cursor is opaque; invalid values return 400 `CURSOR_INVALID`. New submissions do not shift an existing cursor. Status changes between requests naturally change filtered membership.

`GET /api/v1/admin/reports/{id}` returns the existing report shape. `GET /api/v1/admin/reports/{id}/audit` returns chronological records with `id`, `reportId`, `adminId`, `action`, `outcome`, `note`, `occurredAt`.

## Decisions and effect ownership

`POST /api/v1/admin/reports/{id}/resolve` accepts `{action:"HIDE_POST",resolutionNote:"Confirmed violation"}`. Actions:

| Action | Target | Owning service effect |
| --- | --- | --- |
| HIDE_POST | Post | Internal post status HIDDEN; excluded from ordinary detail/feed/widget queries |
| WARN_USER | User | Idempotent REPORT_RESULT notification to target containing the warning note |
| BAN_USER | User | Internal identity status BANNED; login/refresh deny and common active-account gate denies existing access JWTs |
| REJECT | Either | Closes as REJECTED without a target mutation |

`REJECT_REPORT` remains the persisted/response action and an accepted request spelling; `REJECT` is its input alias. Legacy DELETE_POST also remains supported. `POST .../{id}/reject` accepts `{resolutionNote:"No violation found"}`. NONE and target/action mismatch are rejected before an action intent is saved.

Moderation never reads/writes users, posts or notification tables. Changes go through authenticated owning-service APIs. Hidden media URLs already obtained from public object storage are not revoked by hiding a post. The shared security gate checks current account status on public REST requests. Chat also checks current state on CONNECT/SEND/SUBSCRIBE and outbound broker MESSAGE delivery, so already-connected banned sessions cannot send, subscribe or continue receiving; account-state lookup failures deny access.

## Durable failure and retry behavior

1. A pessimistic report lock records the admin, action and normalized note, changes PENDING to INVESTIGATING and appends REQUESTED in an independent committed transaction.
2. A second locked transaction calls the owner and only then closes the report and appends APPLIED. Concurrent identical resolves cannot execute the remote effect twice after a successful local commit.
3. Timeout/failure rolls back phase 2, appends DOWNSTREAM_UNCONFIRMED in another transaction, and returns 502 `MODERATION_DOWNSTREAM_UNCONFIRMED`. INVESTIGATING is intentionally not a claim that the remote effect failed: a timeout can happen after the owner committed.
4. Retry must use the original admin/action/note. Attempts to change a recorded decision receive 409 `REPORT_DECISION_IMMUTABLE`. Ban/hide use state-setting APIs; warning and reporter-result notifications use separate deterministic idempotency keys. An uncertain remote commit can be repeated safely.
5. Reporter notification runs after the committed decision. Failure leaves `notification_delivered=false`, without undoing a successful ban/hide. Identical resolve replay or the scheduled recovery worker retries it. RESULT_NOTIFIED is appended only after confirmed delivery to notification-service (push delivery itself follows that service's lifecycle).
6. A bounded worker polls up to 50 eligible unfinished intents plus 50 eligible result notifications every `app.moderation.recovery-delay-ms` (default 10000). Each committed attempt moves its next eligible time using exponential backoff from 10 seconds up to 15 minutes. Ordering by next eligible time and report ID lets later work progress while persistent failures wait. It preserves the original actor/note and uses the same locks. There is no compensating unban/unhide on an ambiguous remote outcome; operator investigation is needed for persistent owner failures.

All audit rows are insert-only at the runtime database principal and protected by an UPDATE/DELETE trigger, including against owner-issued mutations. Audit retains the actor ID even if an admin is removed. Final report decision fields are immutable through application operations.

## Migration and verification

V11 adds duplicate indexes, a queue index, the durable result-notification marker and `moderation_audit` with service-specific grants. If existing reports contain duplicate reporter/target pairs, migration stops without deleting records; reconcile those duplicates explicitly before rollout.

Run `mvn -pl moderation-service -am test`. `ModerationPostgresTests` creates its own disposable PostgreSQL 16 container and migrates V1 and V11, with application connections using `moderation_service_user`; no shared database is used. Docker-free runs skip these integration tests and do not constitute database/concurrency evidence. Tests cover concurrent duplicate submissions, durable unconfirmed outcome/retry, concurrent resolution, queue execution, immutable audit enforcement and progress beyond a permanently failing 50-report recovery batch. Unit tests cover notes, targets, warning/result retry and admin-token-type isolation.
