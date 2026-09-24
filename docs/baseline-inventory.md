# LocCoc repository baseline inventory

Updated 24 September 2026 for Trello card `LC-S0-01`.

## Verified local commits and branches

| Branch | Commit | Verified contents | Status |
| --- | --- | --- | --- |
| `origin/main` | `98e3939` | Root README only | GitHub default branch |
| `origin/dev` | `98e3939` | Integration baseline from GitHub `main` | Published |
| `origin/mobile-app` | `99c9fe9` | Generated Flutter application plus repository/iOS ignore rules | Published and tested |
| `main` | `3ffef7d` | Root README only | Local branch exists |
| `dev` | `3ffef7d` | Same baseline as `main` | Local branch exists |
| `mobile-app` | `7c8705f` | Generated Flutter application for Android and iOS | Local commit exists |
| `chore/LC-S0-01-baseline-inventory` | Based on `dev` | Baseline audit, project plan, and repository documentation | In progress |

## Source inventory

| Area | Location | Verification |
| --- | --- | --- |
| Project plan | `LocCoc_Project_Plan_3_Members.md` | Present |
| Flutter app | `loccoc_application/` on `mobile-app` | Generated source present |
| Android project | `loccoc_application/android/` on `mobile-app` | Present |
| iOS project | `loccoc_application/ios/` on `mobile-app` | Present |
| Gateway | Not found | Unverified |
| User Service | Not found | Unverified |
| Post Service | Not found | Unverified |
| Chat Service | Not found | Unverified |
| Notification Service | Not found | Unverified |
| Subscription Service | Not found | Unverified |
| Admin Service | Not found | Unverified |
| Docker Compose | Not found | Unverified |

## Remote observations

- The GitHub `main` ref is readable at commit `98e3939`.
- Local `main` and `dev` point to `3ffef7d`. The local and GitHub root commits
  have the same initial tree but no shared commit ancestor.
- The GitHub `dev` and `mobile-app` refs were published and verified on
  24 September 2026.
- A clean clone can check out `dev`, `mobile-app`, and
  `chore/LC-S0-01-baseline-inventory`.
- A fresh clone of `origin/mobile-app` at `99c9fe9` passed `flutter pub get`,
  `flutter analyze`, and `flutter test` on Flutter 3.47.5 (Dart 3.13.4) on
  24 September 2026. Analyze reported no issues and the widget smoke test
  passed 1/1.
- GitHub `main` requires a pull request, one approval, resolved conversations,
  and disallows force-pushes and deletion.
- No backend branch, tag, or commit has been verified.
- The planned tag `backend-baseline-v0.1.0` must not be created until a
  buildable backend commit is located.
- Do not force-push `main`. Build publishable branches from `origin/main` and
  apply the verified local task commits on top.

## Backend recovery investigation

- The Notion [backend implementation tracker](https://app.notion.com/p/3a67b662e4cd8109a9f4d5dde79c827a)
  reports a completed Maven multi-module backend, Flyway migrations, Docker
  Compose health checks, and passing tests, but provides no repository URL or
  commit SHA. Those completion claims remain unverified.
- The Notion [architecture and sprint plan](https://app.notion.com/p/e1c15a30f6534d32b2bb13bb99af0514)
  names the expected local project `social-media-ai-backend` and its Gateway
  and service modules.
- A matching original local repository was located under
  `Documents/App/LocCoc/social-media-ai-backend`, but the current automation
  session cannot read it because macOS privacy controls deny access.
- An accessible recovery worktree contains only an earlier 110-file scaffold:
  no root Maven aggregator, service application shells only, and a Compose
  file containing PostgreSQL and Redis rather than the complete stack. It must
  not be published as the completed backend baseline.
- Backend verification is therefore blocked until the original repository can
  be read or an equivalent archive/remote with Git metadata is supplied.

## Required evidence before completing LC-S0-01

- [ ] Locate the backend source referenced by the Notion tracker.
- [ ] Verify that the backend source builds and identify its commit history.
- [x] Publish `dev` and `mobile-app` to GitHub.
- [x] Require pull requests for changes to `main`.
- [x] Confirm `git ls-remote` returns both `main` and `dev`.
- [x] Re-run Flutter dependency resolution, static analysis, and tests from a
  clean GitHub clone at `origin/mobile-app` commit `99c9fe9`.
- [ ] Verify Docker Compose configuration when backend source is available.
- [ ] Create `backend-baseline-v0.1.0` on a buildable backend commit.
- [ ] Record commit SHA, scope, and test evidence for each service.

Anything not supported by a commit and test evidence remains incomplete.
