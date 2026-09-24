# LocCoc

LocCoc is a private social application planned for a three-person team. The MVP
uses one Flutter application and six backend service boundaries: User, Post,
Chat, Notification, Subscription, and Admin.

## Repository status

- `main`: stable integration baseline.
- `dev`: integration branch for sprint work.
- `mobile-app`: initial Flutter application generated for Android and iOS.
- Backend source has not been found in this repository yet. Do not mark backend
  work complete until its source and Git history are verified.

The detailed implementation plan is in
[`LocCoc_Project_Plan_3_Members.md`](LocCoc_Project_Plan_3_Members.md). The
verified baseline inventory is in
[`docs/baseline-inventory.md`](docs/baseline-inventory.md).

## Run the mobile application

```bash
git switch mobile-app
cd loccoc_application
flutter pub get
flutter analyze
flutter test
flutter run
```

Use an Android emulator or an iOS simulator for `flutter run`.

## Branch workflow

1. Start each Trello card from `dev` using a branch that contains the card ID,
   for example `chore/LC-S0-01-baseline-inventory`.
2. Keep commits scoped to one Trello card.
3. Open a pull request to `dev` and attach test evidence.
4. Merge `dev` to `main` only after the sprint acceptance criteria pass.

## Current next step

Complete `LC-S0-01` by locating the backend source recorded in Notion,
publishing the verified history, and confirming `main` and `dev` on GitHub.
