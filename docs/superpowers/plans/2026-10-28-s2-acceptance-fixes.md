# S2-02 / S2-03 acceptance fixes

Goal: finish the existing composer/feed scope without expanding other sprint work.
Spec: ../specs/2026-10-25-sprint-s2-02-03-design.md.
Work in the existing S2 worktree and preserve Antigravity's pending changes.

- [x] Add regression tests for equal-timestamp pagination, cross-filter deletion, and partial cached pages; observe failures.
- [x] Align cache/controller absence detection with the backend ordering `(createdAt DESC, id DESC)`. Never treat truncation at the cache bound as an exhaustive response.
- [x] Diagnose native integration hangs; separate simulator camera availability from denied permission and run bounded native tests with captured logs.
- [x] Run live Gateway/PostService multi-account composer/feed/detail/save/cache checks on iOS and Android; fix test or product failures at their source.
- [x] Run format, analyzer, full Flutter tests, required backend/policy checks and supported builds; record actual acceptance evidence.
- [ ] Commit/push the scoped changes to PR #5, verify CI and update Trello. Only mark Done when board acceptance and integration gates hold.

Review focus: posts moved to page two; stale content in another filter; equal creation times; account changes while requests are in flight; retry after an ambiguous publication.

Final review: separate read-only reviewer. Fixed both Important findings in one pass: partial-page cross-filter cache resurrection (RED→GREEN regression), and app-global Android lost-picker recovery ownership (RED→GREEN regression plus same-owner/mismatched-owner/launch-marker tests). Final Flutter suite: 102/102. No deferred findings. Physical hardware and staging are not claimed by simulator/local QA evidence.
