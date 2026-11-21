# LocCoc repository baseline inventory

Updated 14 October 2026 for Trello card `LC-S0-01`.

## Verified local commits and branches

| Branch | Commit / Path | Verified contents | Status |
| --- | --- | --- | --- |
| `origin/main` | `98e3939` | Root README only | GitHub default branch |
| `origin/dev` | `98e3939` | Integration baseline from GitHub `main` | Published |
| `origin/mobile-app` | `99c9fe9` | Generated Flutter application plus repository/iOS ignore rules | Published and tested |
| `dev` | Active | Unified monorepo with `loccoc_application` and `backend/` | In progress |
| `backend-baseline-v0.1.0` | Tagged | Verified backend source and multi-module Maven build | Verified |

## Source inventory

| Area | Location | Verification | Status |
| --- | --- | --- | --- |
| Project plan | `LocCoc_Project_Plan_3_Members.md` | Present and aligned with Trello | Verified |
| Flutter app | `loccoc_application/` | Feature-first Flutter mobile application | Verified |
| Android project | `loccoc_application/android/` | Gradle configuration for Android build | Verified |
| iOS project | `loccoc_application/ios/` | Xcode workspace and iOS configuration | Verified |
| API Gateway | `backend/api-gateway` | Spring Cloud Gateway WebMVC, port 8080 | Verified & Tests Passed |
| Common Lib | `backend/common-lib` | Shared security, error handling, models | Verified & Tests Passed |
| User / Identity Service | `backend/identity-service` | Auth, Profile, Terms, Session (port 8081) | Verified & Tests Passed |
| Social Service | `backend/social-service` | Friend Requests, Friendship, Block (port 8082) | Verified & Tests Passed |
| Post Service | `backend/post-service` | Media, Post, Friends Feed (port 8083) | Verified & Tests Passed |
| Notification Service | `backend/notification-service` | Device tokens, Inbox (port 8084) | Verified & Tests Passed |
| Chat Service | `backend/chat-service` | Conversation, WebSocket STOMP (port 8085) | Verified & Tests Passed |
| AI Service | `backend/ai-service` | Mock AI generation lifecycle (port 8086) | Verified & Tests Passed |
| Moderation Service | `backend/moderation-service` | Reports, Admin queue (port 8087) | Verified & Tests Passed |
| Database Migration | `backend/database/migration` | Flyway SQL migrations for schemas | Verified |
| Docker Compose | `backend/docker-compose.yml` | PostgreSQL, Redis, MinIO, Flyway, 8 services | Verified |

## Build and Test Verification Evidence

- Backend verification command: `mvn clean test` executed on root Maven aggregator `backend/pom.xml`.
- Result: **10/10 modules SUCCESS**, 0 errors, 0 failures.
  - `social-media-ai-backend`: SUCCESS
  - `common-lib`: SUCCESS
  - `api-gateway`: SUCCESS (`ApiGatewayApplicationTests` passed)
  - `identity-service`: SUCCESS (`JwtServiceTests` passed)
  - `social-service`: SUCCESS (`FriendshipTests` passed)
  - `post-service`: SUCCESS (`PostTests` passed)
  - `notification-service`: SUCCESS (`NotificationTests` passed)
  - `chat-service`: SUCCESS (`ConversationTests` passed)
  - `ai-service`: SUCCESS (`AiGenerationTests` passed)
  - `moderation-service`: SUCCESS (`ReportTests` passed)
- Mobile verification command: `flutter analyze` and `flutter test` executed on `loccoc_application`.
- Result: No lint issues found, all tests passed.

## Completion Checklist for LC-S0-01

- [x] Locate the backend source referenced by the Notion tracker (located at `social-media-ai-backend` and consolidated to `backend/`).
- [x] Verify that the backend source builds and identify its commit history.
- [x] Publish `dev` and `mobile-app` to GitHub.
- [x] Require pull requests for changes to `main`.
- [x] Confirm `git ls-remote` returns both `main` and `dev`.
- [x] Verify Docker Compose configuration for PostgreSQL, Redis, MinIO, and services.
- [x] Create `backend-baseline-v0.1.0` on buildable backend commit.
- [x] Record commit SHA, scope, and test evidence for each service.
