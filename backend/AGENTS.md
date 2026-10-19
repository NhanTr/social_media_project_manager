# AGENTS.md

Hướng dẫn này áp dụng cho toàn bộ repository `social-media-ai-backend`. Các agent và contributor phải đọc file này trước khi thay đổi code.

## 1. Tổng quan dự án

LocCoc là backend microservices cho ứng dụng mạng xã hội chia sẻ ảnh/GIF ngắn, có kết bạn, feed friends-only, chat realtime, notification, AI generation và moderation.

Stack chính:

- Java 21.
- Spring Boot 4.1.
- Spring Cloud Gateway Server Web MVC.
- PostgreSQL dùng chung theo ownership của từng service.
- Redis cho OTP và refresh-token session.
- MinIO cho media object storage.
- Flyway quản lý schema.
- Maven multi-module.
- Docker Compose cho môi trường local.

Đọc tài liệu kỹ thuật theo thứ tự:

1. `docs/README.md`
2. `docs/01-system-architecture.md`
3. `docs/02-business-flows.md`
4. `docs/03-api-catalog.md`
5. `docs/04-service-responsibilities.md`
6. `docs/05-gaps-and-roadmap.md`

Khi tài liệu và implementation khác nhau, xác minh controller, service, security config và migration hiện tại trước khi sửa. Cập nhật tài liệu cùng thay đổi contract.

## 2. Cấu trúc repository

```text
social-media-ai-backend/
├── api-gateway/             # Public entry point và route
├── identity-service/        # Auth, user, terms, account deletion
├── social-service/          # Friend request, friendship, block
├── post-service/            # Media, post, feed, widget
├── notification-service/    # Device token và notification
├── chat-service/            # Conversation, message, STOMP WebSocket
├── ai-service/              # AI generation job
├── moderation-service/      # Report và admin moderation
├── common-lib/              # API/security/web cross-cutting code
├── database/migration/      # Flyway migrations dùng chung
├── docs/                    # Kiến trúc, flow và API contract
├── docker-compose.yml
├── Dockerfile.service
└── pom.xml                  # Root Maven aggregator
```

Không tạo business domain mới trong `common-lib`. Không đặt entity, repository hoặc business service trong `api-gateway`.

## 3. Service và ownership

| Module | Port | Dữ liệu sở hữu | Trách nhiệm |
| --- | ---: | --- | --- |
| `api-gateway` | 8080 | Không có DB | Route, JWT gate, request ID |
| `identity-service` | 8081 | `users`, `terms_versions`, `user_terms_acceptance`, `account_deletion_requests` | Auth, profile, terms, deletion |
| `social-service` | 8082 | `friend_requests`, `friendships`, `blocks` | Social graph |
| `post-service` | 8083 | `posts`, `saved_posts` | Media, post, feed, widget |
| `notification-service` | 8084 | `notifications`, `device_tokens` | Notification và device registration |
| `chat-service` | 8085 | `conversations`, `messages` | REST/STOMP chat |
| `ai-service` | 8086 | `ai_generations` | AI processing lifecycle |
| `moderation-service` | 8087 | `reports`, `admins` | Report, admin action |

Quy tắc ownership:

- Chỉ service sở hữu bảng mới được ghi bảng đó.
- Service khác lấy hoặc thay đổi dữ liệu qua internal API.
- Không thêm repository đọc/ghi bảng của domain khác.
- Không tạo business join xuyên ownership.
- Giao tiếp liên service hiện dùng REST đồng bộ.
- Notification/push là side effect; không để lỗi push làm mất transaction nghiệp vụ chính.

## 4. API conventions

### Public API

- Prefix: `/api/v1/**`.
- Mobile/admin client gọi qua Gateway cổng 8080.
- Endpoint private yêu cầu `Authorization: Bearer <token>`.
- Endpoint user và admin phải kiểm tra quyền ở domain service, không chỉ dựa vào Gateway.

### Internal API

- Prefix: `/internal/v1/**`.
- Không route qua Gateway.
- Yêu cầu header `X-Internal-Api-Key`.
- Chỉ dùng trong service-to-service communication.
- Không trả internal API key cho client hoặc ghi key vào log.

### WebSocket

- STOMP endpoint: `/ws/chat`.
- CONNECT frame gửi `Authorization: Bearer <access-token>`.
- Send destination: `/app/chat.send`.
- Subscribe destination: `/topic/conversations/{conversationId}`.
- Luôn kiểm tra membership trước khi subscribe/gửi message.

### Request và response

- Request DTO phải có Jakarta Validation.
- List lớn dùng cursor pagination và `PageResponse<T>`.
- Cursor là opaque; không tạo contract phụ thuộc cấu trúc encode nội bộ.
- API lỗi dùng `ProblemDetail` và error code ổn định.
- Giữ `X-Request-Id` xuyên suốt request và response.
- Không đưa token, OTP, password, internal key hoặc PII nhạy cảm vào log.
- POST có khả năng retry phải có idempotency strategy.

### Endpoint đang dùng

- Account deletion hiện là `POST /api/v1/account/delete-request`.
- Request trace hiện dùng `X-Request-Id`, không phải `X-Correlation-Id`.
- Gateway hiện là Web MVC; không dùng WebFlux API/config nếu chưa chủ động migrate toàn module.
- Media upload hiện là multipart qua `/api/v1/media/upload`; presigned upload mới chỉ nằm trong roadmap.

## 5. Database và migration

- PostgreSQL schema được quản lý bằng `database/migration/V*.sql`.
- Không dùng Hibernate `ddl-auto` để thay schema; JPA phải tiếp tục ở chế độ `validate`.
- Mọi thay đổi schema cần migration Flyway mới, không sửa migration đã chạy ở môi trường chia sẻ.
- Migration cần giữ đúng foreign key, enum, index, unique constraint và DB ownership/grant.
- Dùng tên migration dạng `V<next>__short_description.sql`.
- OTP và refresh-token session thuộc Redis, không thêm bảng PostgreSQL cho chúng nếu chưa có quyết định kiến trúc mới.
- Widget không có bảng riêng.
- Media binary không lưu trong PostgreSQL.

## 6. Security

- Gateway và từng downstream service đều xác thực JWT.
- `identity-service` là service duy nhất phát hành user access/refresh token.
- `moderation-service` phát hành admin token qua admin login.
- Internal authentication sử dụng role `SERVICE` sau khi kiểm tra internal API key.
- Public allowlist phải được cập nhật đồng thời tại Gateway và service đích.
- Không tin `X-User-Id` hoặc role header do client gửi; lấy identity từ JWT principal.
- Kiểm tra ownership, friendship, block, visibility và admin role tại service sở hữu nghiệp vụ.
- Secret mặc định chỉ dành cho local. Không hard-code credential mới.

## 7. Quy ước triển khai

- Tôn trọng package hiện tại `com.nhantr.<service_name>`.
- Controller chỉ nhận/validate request và gọi application/domain service.
- Business rule đặt trong service.
- Repository chỉ phục vụ aggregate/domain của service đó.
- REST client đặt trong package `client`.
- Config security/WebSocket/provider đặt trong `config`.
- Dùng enum hiện hữu thay vì magic string khi ở cùng service.
- Internal client phải gửi `X-Internal-Api-Key`.
- Side effect liên service cần cân nhắc timeout, idempotency và failure policy.
- Không tự động retry POST tạo dữ liệu tại Gateway.
- Khi thêm API, cập nhật OpenAPI-visible DTO, `docs/03-api-catalog.md` và flow liên quan.
- Khi thay ranh giới service hoặc dependency, cập nhật `docs/01-system-architecture.md` và `docs/04-service-responsibilities.md`.
- Khi hoàn thành một mục roadmap, cập nhật `docs/05-gaps-and-roadmap.md`.

## 8. Quy trình thay đổi tính năng

Trước khi code:

1. Xác định use case và service owner.
2. Kiểm tra API public/internal liên quan.
3. Kiểm tra schema và migration hiện tại.
4. Kiểm tra các caller/callee liên service.
5. Xác định authorization và failure behavior.

Trong khi code:

1. Viết hoặc cập nhật API contract.
2. Thêm migration nếu cần.
3. Implement trong đúng service owner.
4. Thêm validation và error code.
5. Thêm test cho happy path, authorization và lỗi domain chính.
6. Cập nhật tài liệu.

Không thực hiện refactor rộng hoặc đổi contract ngoài phạm vi nếu không cần cho tác vụ hiện tại.

## 9. Kiểm tra trước khi bàn giao

Chạy từ thư mục gốc:

```bash
mvn test
docker compose config --quiet
git diff --check
```

Khi thay đổi một module và muốn kiểm tra nhanh:

```bash
mvn -pl <module> -am test
```

Khi thay đổi runtime/integration:

```bash
docker compose up --build -d
docker compose ps
curl http://localhost:8080/actuator/health
```

Không dùng `docker compose down -v` trừ khi người dùng chủ động yêu cầu xoá dữ liệu local.

Swagger của mỗi service:

```text
http://localhost:<service-port>/swagger-ui.html
http://localhost:<service-port>/v3/api-docs
```

## 10. Trạng thái và khoảng trống đã biết

Phạm vi demo/MVP đã có đủ 7 domain service, nhưng các adapter production sau vẫn chưa hoàn chỉnh:

- SMS OTP provider.
- FCM/APNs push provider.
- AI provider, durable queue, quota và safety.
- Presigned media upload và media processing pipeline.
- Chat delivered receipt và external message broker.
- Account purge worker/cancel/status.
- Deep-link friend invite.
- Gateway rate limiting, timeout, request-size và CORS production.
- Moderation audit trail và admin pagination.

Không mô tả mock hiện tại là production-ready. Khi làm các mục này, ưu tiên contract, idempotency, observability và failure recovery.

## 11. Bảo toàn worktree

Repository có thể chứa thay đổi chưa commit thuộc về người dùng hoặc tác vụ trước.

- Luôn kiểm tra `git status --short` trước khi sửa.
- Không reset, checkout, xoá hoặc ghi đè thay đổi không thuộc tác vụ.
- Chỉ chỉnh file cần thiết.
- Nếu file mục tiêu đã có thay đổi chồng lấn, đọc diff và tích hợp cẩn thận.
- Không dùng lệnh phá huỷ dữ liệu hoặc lịch sử Git nếu chưa được yêu cầu rõ ràng.
