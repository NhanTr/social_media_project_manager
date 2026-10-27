# Sprint S2-02 / S2-03 implementation and acceptance

Date: 25 October 2026 (Asia/Ho_Chi_Minh).

Follow-up: [28 October verification](sprint-s2-02-03-review-2026-10-28.md) records the later bug fixes, expanded suite, native simulator/emulator acceptance, and actual process-kill recovery. Its results supersede the earlier unexecuted simulator cases below; physical-device and staging boundaries remain explicit.

Cards: [LC-S2-02](https://trello.com/c/SvmP4cq8), [LC-S2-03](https://trello.com/c/0fxRbH5Y).

Implementation branch: `feature/LC-S2-02-LC-S2-03`, based on dev `0ba8cb6`.

## Delivered behavior

- Feed tab now opens real friend posts, an individual-friend filter, refresh, cursor load-more, loading skeleton, empty/unavailable states and a bounded account/filter cache with available preview bytes and offline timestamp.
- Camera/gallery composer provides original/square crop with adjustable offsets, original/monochrome/warm output filters, 2048px maximum edge, JPEG below 5 MiB, API-compatible caption validation and durable drafts. Android lost picker recovery is handled when opening composer.
- Upload uses real presigned PUT byte progress with abort, finalized media metadata and an immutable per-publication UUID. Ambiguous create responses keep the payload locked for safe reconciliation; explicit initial validation rejection allows correction. Successful publication acknowledges the returned own post and refreshes the friends feed, without injecting an own post into friends-only results.
- Media detail rechecks access, supports zoom, and saves through the native Photos adapter after repeated authorization checks. Logout/social changes invalidate visible content and feed snapshots.
- Backend integrates S2-01 media/post lifecycle using new migrations and adds owner/finalized-upload binding, request fingerprinting, per-account UUID uniqueness/transaction serialization, immutable finalized object copy and consumed-media cleanup protection.

## Database compatibility

V1–V4 on dev remain unchanged. `V5__post_media_lifecycle.sql` integrates media lifecycle; `V6__post_publication_idempotency.sql` adds publication metadata. A database that applied `V3__post_media_lifecycle.sql` from the separate S2-01 branch is a different migration history: do not point this branch at that database without an explicit reconciliation plan. No existing local database was migrated or deleted during these tests; PostgreSQL/MinIO checks use task-owned disposable containers.

## Local media endpoints

The device must reach both the Gateway and the exact MinIO public host used to sign upload URLs. Do not rewrite signed URLs in Flutter.

- iOS simulator: Gateway `http://localhost:8080`, `MINIO_PUBLIC_URL=http://localhost:9000`.
- Android emulator: Gateway `http://10.0.2.2:8080`, start backend with `MINIO_PUBLIC_URL=http://10.0.2.2:9000` (Compose now accepts this override).
- Physical phone: set `API_BASE_URL` and backend `MINIO_PUBLIC_URL` to reachable HTTPS hosts. Production transport policy is unchanged; Android cleartext exception is debug-only and iOS allows local networking for simulator development.

Example for the Android emulator: `MINIO_PUBLIC_URL=http://10.0.2.2:9000 docker compose up --build -d`, then `flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080` from `loccoc_application`. This starts the normal development stack; inspect your existing DB migration history first.

## Evidence matrix

| Acceptance | Automated evidence | Device boundary |
| --- | --- | --- |
| Permission denial has guidance | Composer/platform error mapping; gallery denied/restricted unit and widget cases | Real camera/Photos denial still requires device acceptance |
| Large image compression | Real JPEG transform/resize/crop/filter/orientation tests, output byte and dimension bounds | Full-resolution camera/HEIC selection should be checked on a phone |
| No duplicate publish after interruption | Durable frozen request restore, lost response retry, live HTTP PUT abort/progress, real PostgreSQL concurrent UUID replay | OS process kill during native picker/network remains manual |
| Draft survives restart | Reconstructed controller/storage, real atomic filesystem snapshots, account isolation | Process-kill smoke still manual |
| Friend filter and pagination | Real HTTP client contract, controller stale response and overlap tests, backend authorization/cursor tests | Two-account UI session smoke remains manual |
| Refresh removes deleted/blocked data | Controller/cache invalidation, cross-filter denial and open-detail authorization tests | Confirm against two active accounts before Done |
| Save image with Photos permission | Authorization before prompt, after prompt and after download; bounded localhost streaming transport; native adapter smoke below | Real denied/restricted/limited settings need device acceptance |
| Offline cache | Durable bounded account/filter snapshots and actual cached image bytes, offline widget tests | Airplane-mode phone smoke remains manual |

## Final verification

Base commit on `origin/dev`: `0ba8cb6`.

1. **Flutter static & unit/widget suite (`loccoc_application`)**:
   - `dart format --output=none --set-exit-if-changed lib test` → **PASS** (`48 files, 0 changed`).
   - `flutter analyze` → **PASS** (`No issues found!`).
   - `flutter test --coverage --timeout 30s` → **PASS** (`90/90 passed`, including composer 25 tests, feed/media-detail 32 tests, shared API/local-store 6 tests, shell capture-to-feed/invalidation/account-isolation 4 tests, and existing Sprint 0–1 tests 23 tests).
2. **Flutter build & simulator native adapter smoke (`loccoc_application`)**:
   - `flutter build web --dart-define=APP_ENV=dev` → **PASS** (`build/web`).
   - `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" flutter build apk --debug --dart-define=APP_ENV=dev` → **PASS** (`build/app/outputs/flutter-apk/app-debug.apk`).
   - `flutter build ios --simulator --dart-define=APP_ENV=dev` → **PASS** (`build/ios/iphonesimulator/Runner.app`).
   - `xcrun simctl install 4B33612E-170F-40A4-B128-5B2A17692151 build/ios/iphonesimulator/Runner.app && xcrun simctl privacy 4B33612E-170F-40A4-B128-5B2A17692151 grant photos-add com.example.loccocApplication && flutter test integration_test/native_media_smoke_test.dart -d 4B33612E-170F-40A4-B128-5B2A17692151` → **PASS** (`1/1 passed` on iPhone 16 Pro iOS 18.3 simulator).
3. **Backend lint, unit & disposable container integration (`backend`)**:
   - `mvn -B -DskipTests checkstyle:check` → **PASS** (`0 Checkstyle violations` across 10 modules).
   - `LOCCOC_MEDIA_STORAGE_IT=true mvn -DargLine=-javaagent:/Users/nhantr/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar test` → **PASS** (`63/63 passed`, 0 failures/errors/skips; includes disposable PostgreSQL 16 V1–V6 migrations, Hibernate schema validation, concurrent publication UUID lock/uniqueness, and disposable MinIO presigned PUT/finalize/cleanup checks).
   - `docker compose config --quiet` → **PASS**.
4. **Repository policy & hygiene**:
   - `python3 -m unittest discover -s scripts/tests -v` → **PASS** (`16/16 passed`).
   - `python3 scripts/validate_environment_config.py backend/.env.example backend/.env.staging.example backend/.env.prod.example` → **PASS** (`all valid`).
   - `git diff --check` → **PASS** (exit 0).

Targeted green tests and simulator plugin smoke are not claimed as multi-account live-backend end-to-end or physical-device hardware evidence.

## Device acceptance before Done

1. On Android and iOS, deny camera/gallery access, observe guidance, then allow and capture/select a full-resolution photo. Verify crop/filter output matches preview and caption boundaries.
2. Kill/reopen the app after image selection and during upload; restore the draft and retry. Verify the same publication appears only once. Repeat on Android with activity destruction while the picker is open.
3. With two friend accounts, verify individual filtering, pagination and refresh. Delete a post and block/unfriend; confirm feed and any open detail disappear and old snapshots do not return offline.
4. Exercise Photos add-only allowed, denied, restricted and limited settings; confirm a saved file actually appears in the device library. Confirm unavailable posts cannot be saved.
5. Verify phone airplane-mode cache, account switch isolation, target-branch CI and merge. Only then move both cards to “05 Hoàn thành”.

## Reversal

Revert the application commits for a code rollback. Leave additive database migrations in place; do not edit migrations already applied and do not drop media/post data as part of rollback. New publication fields are optional for legacy clients, but retain the reliability path for clients already retrying a pending immutable request.
