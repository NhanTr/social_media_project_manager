# LocCoc

Monorepo: Flutter trong `loccoc_application/`, Java 21/Maven backend trong `backend/`.
Gateway cổng 8080; identity 8081, social 8082, post 8083, notification 8084,
chat 8085, AI 8086 và moderation 8087. Subscription vẫn nằm trong kế hoạch Sprint 4.

## Chạy local

```sh
cd backend
mvn test
docker compose up --build -d
docker compose ps -a
curl -f http://localhost:8080/actuator/health
```

Trong terminal khác:

```sh
cd loccoc_application
flutter pub get
flutter analyze
flutter test --timeout 30s
flutter run
```

Android emulator mặc định dùng `10.0.2.2:8080`, iOS simulator dùng `localhost:8080`.
Có thể truyền `--dart-define=API_BASE_URL=https://... --dart-define=APP_ENV=staging`.
Local OTP mock được trả trong `devOtp`; UI tự điền khi backend bật `OTP_EXPOSE_CODE`.
Không dùng credentials local mặc định cho staging/production.

## Google và secure storage

Google dùng SDK Google Sign-In lấy ID token thật. Chạy Flutter với
`--dart-define=GOOGLE_SERVER_CLIENT_ID=<web-client-id>` và backend
`GOOGLE_CLIENT_ID` cùng giá trị. Android cần OAuth client khớp package và SHA signing.
iOS cần `--dart-define=GOOGLE_IOS_CLIENT_ID=<ios-client-id>` và URL scheme
reversed client ID trong Runner Info.plist theo cấu hình OAuth của dự án.
Chưa có OAuth config thì dùng OTP; app không tự tạo Google token giả.

Token lưu bằng flutter_secure_storage; iOS có Keychain entitlements, Android tắt backup.
Phiên cũ lưu plaintext sẽ bị loại bỏ, cần đăng nhập lại sau nâng cấp.
Cần xác nhận trên Android/iOS thật hoặc simulator; unit test dùng storage giả lập.

## Git và nghiệm thu

- Tạo branch có mã Trello từ dev, ví dụ `fix/LC-S1-02-auth-session`.
- Commit/PR title theo Conventional Commits và có mã task.
- PR vào dev, gắn card, test evidence và rollback note; có người khác review.
- Chỉ merge vào main và chuyển Done khi CI cùng acceptance criteria đạt.
- Không force push hoặc xóa volume dữ liệu để chạy nghiệm thu.

CI bắt buộc kiểm tra Conventional Commit, các mẫu environment, Flutter format,
analyze, test và debug APK, Maven Checkstyle/test/build, migration Flyway, secret
scan và dependency scan mức `HIGH`/`CRITICAL`. Chạy các gate chính trước khi push:

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_conventional_commits.py origin/dev HEAD
python3 scripts/validate_environment_config.py \
  backend/.env.example backend/.env.staging.example backend/.env.prod.example
(cd backend && mvn -B -DskipTests checkstyle:check && mvn -B clean verify)
(cd loccoc_application && flutter analyze && flutter test --timeout 30s \
  && flutter build apk --debug --dart-define=APP_ENV=dev)
```

Xem [hướng dẫn nghiệm thu](docs/sprint-0-1-acceptance-review.md) và
[báo cáo bổ sung](docs/sprint-0-1-followup.md). Các kết quả kiểm tra có thời điểm;
không diễn giải inventory cũ là xác nhận trạng thái hiện tại.
