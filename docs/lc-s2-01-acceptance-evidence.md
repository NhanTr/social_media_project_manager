# LC-S2-01 Acceptance Evidence

## Automated tests

Full post-service test:
Command: cd d:\LocCoc\social_media_project_manager\backend\post-service; mvn test
Result: Tests run: 26, Failures: 0, Errors: 0, Skipped: 1; BUILD SUCCESS
Evidence: docs/lc-s2-01-full-test-output.txt

Integration test:
Command: cd d:\LocCoc\social_media_project_manager\backend\post-service; mvn -Dtest=MediaLifecycleIntegrationTests test
Result: Tests run: 1, Failures: 0, Errors: 0, Skipped: 1; BUILD SUCCESS
Evidence: docs/lc-s2-01-integration-test-output.txt
Skip reason: Testcontainers 2.0.5 considers Docker unavailable (Docker server version should be at least 1.6.0).

Migration coverage:
PostPublicationConcurrencyTests runs V1 through V6 against PostgreSQL 16.
Result: 8 passed, 0 failed.
Evidence: docs/lc-s2-01-concurrency-test-output.txt

## Branch reconciliation
Compared feature/LC-S2-01-review with dev for backend/post-service and backend/database/migration.
dev contains expanded implementation plus V5 and V6.
feature/LC-S2-01-review is older/stale.
Recommendation: remove stale branch after team confirmation.
Evidence: docs/lc-s2-01-branch-diff.txt, docs/lc-s2-01-branch-reverse-diff.txt

## Acceptance status
1 Validate MIME/size/metadata: code done + tests passed (MediaValidationTests 5)
2 Presigned upload/finalize: code done + tests passed (MediaFinalizeReplayTests 1; integration skipped by Testcontainers)
3 Create/update/delete post: code done + tests passed (PublicationContractTests 1; PostPublicationConcurrencyTests 8)
4 Authorize friends via User/Social Service: code done + tests passed (PostAuthorizationTests 6)
5 Cursor pagination/stable sort: code done + tests passed (cursor ordering test)
6 Soft delete/media cleanup job: code done + tests passed (MediaCleanupServiceTests 3)
7 Non-friend cannot read post: code done + tests passed (PostAuthorizationTests 6)
8 Upload not finalized expires: code done + tests passed (expired upload test)
9 Feed no duplicate/missing on pagination: code done + tests passed (cursor ordering); manual overlap pending
10 Delete hides immediately and schedules media cleanup: code done + tests passed (cleanup tests)

## Manual acceptance still pending
- Two-user end-to-end demo through Gateway
- Non-friend read rejection manual verification
- Upload expiration manual verification with real MinIO
- Pagination overlap manual verification with more than 25 posts
- Delete hide plus media cleanup manual verification with real MinIO
- OpenAPI/docs review

## Conclusion
LC-S2-01 is implementation-complete. Automated tests: 26 passed, 1 skipped, 0 failures.
The skipped integration test is due to Testcontainers/Docker Desktop version compatibility.
Manual two-user and real MinIO end-to-end verification remain pending before final card closure.


## Manual acceptance completed on 2026-10-31

Environment: running Docker Compose stack (PostgreSQL, Redis, MinIO, identity/social/post/gateway).

Results:
- User A and User B created via OTP and accepted current Terms.
- A uploaded a PNG through presigned URL; PUT returned 200; finalize succeeded.
- A created a FRIENDS_ONLY post.
- B (non-friend) GET /api/v1/posts/{id} returned 403.
- A sent friend request; B accepted.
- B GET /api/v1/posts/{id} returned 200 with caption "manual test post".
- B GET /api/v1/feed included the post.
- A deleted the post; B GET /api/v1/posts/{id} returned 404.
- media_cleanup_queue contains the deleted upload object key and done_at is populated.
- Pagination manual test: created 3 posts, fetched 3 pages with limit=1, all page IDs unique, no duplicates.

Manual items still not executed:
- Upload expiration with real TTL wait (TTL 600s); covered by automated PostPublicationConcurrencyTests.
- Pagination overlap with more than 25 posts (basic 3-post pagination passed).
- OpenAPI/docs review.

Updated acceptance conclusion: code, automated tests, and core manual flow passed. Branch review remains un-deleted per user request.
