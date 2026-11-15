# LocCoc

Monorepo: Flutter trong `loccoc_application/`, Java 21/Maven backend trong `backend/`.
Gateway cổng 8080; identity 8081, social 8082, post 8083, notification 8084,
chat 8085, AI 8086, moderation 8087 và subscription/payment sandbox 8088.

## Ranh giới MVP đã chốt

- MVP chấp nhận schema `public` dùng chung với ownership/grants.
- Schema riêng theo service được hoãn sang sprint hậu MVP.
- Mock/demo không production-ready.
- Adapter production AI, payment, email và push được hoãn sang Icebox.

Đây là quyết định phạm vi cho MVP/demo, không phải xác nhận hai hạng mục trên đã
hoàn thành. Trước production cần tách schema/credential theo service, diễn tập
migration và backup/restore, đồng thời thay mock bằng adapter có secret
management, idempotency, observability và failure policy. Chi tiết ở
[kiến trúc MVP](docs/architecture/01-mvp-boundaries-and-architecture.md) và
[roadmap backend](backend/docs/05-gaps-and-roadmap.md).

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

## Flutter S3: giao diện Android/iOS

S3-02/S3-04 là màn hình trong app Flutter tại `loccoc_application/`.
Chat và notification đã tích hợp vào app chính; repository mô phỏng được dùng
khi backend đang hoàn thiện. Chạy trên điện thoại/emulator:

```sh
cd loccoc_application
flutter devices
flutter run -d <android-or-ios-device-id> --dart-define=APP_ENV=dev
```

Để xem riêng S3 trên thiết bị khi chưa có backend đăng nhập:

```sh
flutter run -d <android-or-ios-device-id> -t lib/s3_demo.dart --dart-define=APP_ENV=dev
```

Ứng dụng chính bật dữ liệu S3 mô phỏng ở dev sau đăng nhập; truyền
`--dart-define=S3_DEMO=false` để tắt. Staging/prod không tự dùng dữ liệu mô phỏng.
Bản web chỉ là công cụ kiểm tra phụ; đầu ra chính là app Android/iOS.

Xem [cách nghiệm thu và nối backend](docs/sprint-s3-02-04-acceptance.md).

## Backend closeout: subscription, AI, moderation và retention

Subscription/Payment dùng local hosted sandbox, không thu tiền thật. Cấu hình
`SANDBOX_CHECKOUT_BASE_URL` tới HTTPS ingress kết thúc bằng
`/api/v1/payments/sandbox/checkouts`, và `PAYMENT_WEBHOOK_SECRET` riêng cho môi trường.
URL local mặc định mong đợi TLS proxy ở :8443; Compose Gateway ở :8080 vẫn là HTTP.
`PAYMENT_SANDBOX_ENABLED=false` tắt checkout/simulator; profile prod cũng tắt simulator.
Chỉ webhook có chữ ký mới cấp quyền; quay lại app chỉ làm mới snapshot server.
Xem [contract và cấu hình](backend/docs/contracts/lc-s4-01-02-subscription-payment.md).

Chạy nghiệm thu hoàn toàn bằng container/database/temp certificate mới; không dùng
volume dữ liệu local. Script tự mở TLS proxy local, kiểm tra luồng và dọn môi trường:

```sh
cd backend
docker build -t loccoc-minio:7aac2a2c5b7c -f Dockerfile.minio .
LOCCOC_MEDIA_STORAGE_IT=true mvn -B clean verify checkstyle:check
cd ..
python3 scripts/check_backend_closeout.py
```

Cần Docker, Java, Python 3 và openssl. Không dùng bằng chứng này để khẳng định
provider AI/payment production hoặc nghiệm thu browser trên điện thoại thật.
Account retention mặc định disabled/dry-run; media cleanup mặc định dry-run.
Xem [runbook security/retention](backend/docs/contracts/lc-s5-01-security.md) trước khi
chạy apply trên staging. Việc ẩn bài không thu hồi URL media công khai đã phát trước đó.

MinIO local/CI được build từ commit chính thức cố định `7aac2a2c5b7c882e68c1ce017d8256be2feea27f` bằng `backend/Dockerfile.minio`, cùng source/license metadata trong image. Không phụ thuộc image DockerHub/Quay không còn cho anonymous pull. Đây là cấu hình demo/CI, không tự xác nhận vận hành object storage production.
