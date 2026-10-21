# LocCoc Flutter application

## Environment configuration

The app accepts compile-time `APP_ENV` and `API_BASE_URL` values:

- `dev` may omit `API_BASE_URL`; Android emulators use
  `http://10.0.2.2:8080`, while other supported platforms use
  `http://localhost:8080`.
- `staging` requires an absolute HTTP(S) `API_BASE_URL`.
- `prod` requires an absolute HTTPS `API_BASE_URL`.

Examples:

```sh
flutter run --dart-define=APP_ENV=dev
flutter run \
  --dart-define=APP_ENV=staging \
  --dart-define=API_BASE_URL=https://api-staging.example.com
flutter build apk \
  --dart-define=APP_ENV=prod \
  --dart-define=API_BASE_URL=https://api.example.com
```

An invalid environment or URL stops configuration before the API client is
created.

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Learn Flutter](https://docs.flutter.dev/get-started/learn-flutter)
- [Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Flutter learning resources](https://docs.flutter.dev/reference/learning-resources)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
