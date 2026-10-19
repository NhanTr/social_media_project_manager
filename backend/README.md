# LocCoc Social Media AI Backend

Backend microservices cho LocCoc, được triển khai bằng Java 21, Spring Boot 4, PostgreSQL, Redis, MinIO, Flyway và Spring Cloud Gateway.

## Thành phần

| Thành phần | Cổng | Trách nhiệm chính |
| --- | ---: | --- |
| API Gateway | 8080 | Route API, xác thực JWT |
| Identity Service | 8081 | Google/phone OTP, JWT/refresh token, profile, terms, xoá tài khoản |
| Social Service | 8082 | Tìm kiếm, lời mời kết bạn, friendship, block |
| Post Service | 8083 | Upload media, post, friends-only feed, widget |
| Notification Service | 8084 | Device token, notification, idempotency |
| Chat Service | 8085 | Conversation, message history, STOMP WebSocket, read status |
| AI Service | 8086 | AI generation bất đồng bộ và retry |
| Moderation Service | 8087 | Report, admin login, hide/delete post, warn/ban user |
| PostgreSQL | 5432 | Dữ liệu dùng chung, mỗi service có DB role riêng |
| Redis | 6379 | OTP và refresh-token session |
| MinIO | 9000/9001 | Media object storage/console |

## Chạy local bằng Docker

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

API Gateway: `http://localhost:8080`

MinIO Console: `http://localhost:9001`

Tắt cụm dịch vụ:

```bash
docker compose down
```

Dữ liệu nằm trong Docker volumes. Chỉ dùng `docker compose down -v` khi chủ động muốn xoá toàn bộ dữ liệu local.

## Cấu hình

Các biến mẫu nằm trong `.env.example`. Trước khi dùng ngoài local cần thay ít nhất:

- `JWT_SECRET`
- `INTERNAL_API_KEY`
- `POSTGRES_PASSWORD`
- `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`
- `ADMIN_USERNAME`, `ADMIN_PASSWORD`
- `GOOGLE_CLIENT_ID` nếu bật Google Sign-In
- `OTP_EXPOSE_CODE=false` để không trả OTP trong response

## API chính

Tất cả REST API public đi qua Gateway và dùng prefix `/api/v1`.

- Auth: `/auth/google`, `/auth/phone/send-otp`, `/auth/phone/verify-otp`, `/auth/refresh`, `/auth/logout`
- User: `/users/me`, `/terms/latest`, `/terms/accept`, `/account/deletion`
- Social: `/friends`, `/friends/search`, `/friend-requests`, `/blocks`
- Post: `/media/upload`, `/posts`, `/feed`, `/feed/by-friend/{friendId}`, `/widget/latest-post`
- Notification: `/device-tokens`, `/notifications`
- Chat: `/conversations`, `/messages`
- AI: `/ai/generations`
- Moderation: `/reports`, `/admin/auth/login`, `/admin/reports`

Các endpoint không thuộc auth/public yêu cầu:

```http
Authorization: Bearer <access-token>
```

WebSocket STOMP dùng endpoint `ws://localhost:8085/ws/chat`, gửi token trong header `Authorization` của frame `CONNECT`. Client gửi message tới `/app/chat.send` và subscribe `/topic/conversations/{conversationId}`.

## Swagger/OpenAPI

Mỗi service cung cấp:

- OpenAPI JSON: `http://localhost:<service-port>/v3/api-docs`
- Swagger UI: `http://localhost:<service-port>/swagger-ui.html`

Ví dụ Identity: `http://localhost:8081/swagger-ui.html`.

## Luồng kiểm thử nhanh

1. Gửi và xác thực OTP để lấy access/refresh token.
2. Chấp nhận terms version hiện tại.
3. Tạo hai user, gửi và accept friend request.
4. Tạo post; kiểm tra post xuất hiện trong feed của người bạn.
5. Mở conversation, gửi message; kiểm tra history và notification.
6. Tạo AI generation; poll tới `SUCCESS` hoặc `FAILED`, retry khi thất bại.
7. Report một post; đăng nhập admin và resolve bằng `HIDE_POST`.

OTP được lưu trong Redis với TTL 5 phút. Ở local, `OTP_EXPOSE_CODE=true` trả thêm `devOtp` để tiện kiểm thử.

## Database migration

Flyway tự chạy trước các service:

- `V1__init_schema.sql`: schema, enum, index, DB role và terms version đầu tiên.
- `V2__notification_idempotency.sql`: idempotency key cho notification.

JPA chỉ chạy ở chế độ `validate`; thay đổi schema mới phải được thêm bằng migration Flyway.

## Kiểm thử mã nguồn

```bash
mvn test
docker compose config --quiet
```

Health:

```bash
curl http://localhost:8080/actuator/health
```

## Ghi chú production

- OTP hiện dùng adapter local; cần tích hợp SMS provider thật.
- Push notification hiện là mock logger; cần nối FCM/APNs.
- AI worker hiện mô phỏng async; cần thay bằng model/provider và queue thực.
- MinIO và Flyway đang đặt `linux/amd64` để chạy ổn trên Apple Silicon; server Linux amd64 không cần thay đổi.
- Không expose `/internal/v1/**` qua Gateway. Service-to-service dùng `X-Internal-Api-Key`.
