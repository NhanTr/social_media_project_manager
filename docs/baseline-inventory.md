# LocCoc repository baseline inventory

Updated 24 September 2026 for Trello card `LC-S0-01`.

## Verified local commits and branches

| Branch | Commit | Verified contents | Status |
| --- | --- | --- | --- |
| `origin/main` | `98e3939` | Root README only | GitHub default branch |
| `origin/dev` | `98e3939` | Integration baseline from GitHub `main` | Published |
| `origin/mobile-app` | `67a2760` | Generated Flutter application for Android and iOS | Published and tested |
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
- No backend branch, tag, or commit has been verified.
- The planned tag `backend-baseline-v0.1.0` must not be created until a
  buildable backend commit is located.
- Do not force-push `main`. Build publishable branches from `origin/main` and
  apply the verified local task commits on top.

## Required evidence before completing LC-S0-01

- [ ] Locate the backend source referenced by the Notion tracker.
- [ ] Verify that the backend source builds and identify its commit history.
- [x] Publish `dev` and `mobile-app` to GitHub.
- [ ] Require pull requests for changes to `main`.
- [ ] Confirm `git ls-remote` returns both `main` and `dev`.
- [ ] Verify Docker Compose configuration when backend source is available.
- [ ] Create `backend-baseline-v0.1.0` on a buildable backend commit.
- [ ] Record commit SHA, scope, and test evidence for each service.

Anything not supported by a commit and test evidence remains incomplete.
