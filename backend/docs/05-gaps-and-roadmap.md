# Khoảng trống và API cần bổ sung

Tài liệu Notion mô tả MVP 20 use case. Source hiện tại đã có phần lõi, nhưng “tracker 100%” nên được hiểu là hoàn thành phạm vi mock/demo, chưa phải production-ready.

## 0. Quyết định phạm vi MVP đã chốt (21/10/2026)

- MVP chấp nhận schema `public` dùng chung với ownership/grants.
- Schema riêng theo service được hoãn sang sprint hậu MVP.
- Mock/demo không production-ready.
- Adapter production AI, payment, email và push được hoãn sang Icebox.

Điểm đến hậu MVP là schema và credential riêng theo service, có migration,
rollback và backup/restore được diễn tập. Adapter production phải bổ sung quản
lý secret, timeout/retry có idempotency, observability, quota và failure policy.
Cho đến khi các điều kiện đó đạt, AI mock và các tích hợp payment/email/push
không được dùng làm bằng chứng production readiness.

## 1. Sai khác cần thống nhất ngay

| Chủ đề | Thiết kế/README cũ | Code hiện tại | Khuyến nghị |
| --- | --- | --- | --- |
| Gateway runtime | WebFlux | Spring Cloud Gateway Web MVC | Giữ MVC và sửa tài liệu, hoặc lập migration riêng; không trộn config |
| Request trace header | `X-Correlation-Id` | `X-Request-Id` | Chọn một tên; ưu tiên giữ `X-Request-Id` để tránh breaking change |
| Xoá tài khoản | Có nơi ghi `/account/deletion` | `/account/delete-request` | Dùng endpoint theo code trong client |
| Upload media | Presigned URL | Presigned session/finalize + legacy multipart | Đã có MVP lifecycle/idempotency; signed/private access và processing còn hoãn |
| Chat status | Sent/Delivered/Read | Sent và mark Read; delivery receipt chưa đầy đủ | Thiết kế API receipt |

## 2. API P0 nên bổ sung

### 2.1 Deep-link mời bạn

Phục vụ UC-03 “gửi link mời”.

| Method | Path đề xuất | Mục đích |
| --- | --- | --- |
| POST | `/api/v1/friend-invites` | Phát hành invite token/link có TTL |
| GET | `/api/v1/friend-invites/{token}` | Resolve người mời trước khi gửi request |
| POST | `/api/v1/friend-invites/{token}/accept` | Accept trực tiếp sau login |

Yêu cầu: token opaque, có TTL, rate limit, không lộ raw user ID và hoạt động với universal link/app store fallback.

### 2.2 Media lifecycle và resilient publication — đã triển khai MVP

- `POST /api/v1/media/uploads` → presigned PUT → `POST /api/v1/media/uploads/{id}/finalize` (replay owner an toàn, metadata persisted, copy sang published object).
- `POST /api/v1/posts` hỗ trợ cặp `clientRequestId` UUID + `uploadId`; kiểm tra owner/finalized metadata, fingerprint + PostgreSQL lock/unique constraint chống duplicate retry đồng thời. Replay giữ cả trạng thái deleted/hidden, notification chỉ fan-out sau create commit.
- Worker dọn upload abandoned, kể cả finalized chưa dùng; consumed upload được bảo vệ khỏi orphan cleanup. Multipart và legacy create được giữ tương thích.
- Chưa có API hủy upload riêng; cancel phía client dừng request và để TTL cleanup. Private/signed media access, scanning/transcode và durable notification outbox còn ngoài MVP.
- V5/V6 nối sau dev V1–V4. DB đã chạy V3 media trên nhánh S2-01 cần reconcile migration history riêng trước deploy; không đổi checksum hoặc rename migration đã áp dụng.

### 2.3 Quản lý post của owner — đã triển khai MVP

`PUT /api/v1/posts/{id}` sửa caption/filter/visibility; `DELETE /api/v1/posts/{id}` soft-delete và lên lịch xóa media. Cả hai kiểm tra ownership tại post-service. Optimistic edit versioning chưa có.

### 2.4 Chat delivery receipt

| Loại | Destination/path đề xuất | Mục đích |
| --- | --- | --- |
| STOMP send | `/app/chat.delivered` | Client xác nhận đã nhận message |
| REST fallback | `POST /api/v1/messages/{id}/delivered` | Receipt khi không dùng STOMP |
| STOMP event | `/topic/conversations/{id}/receipts` | Đồng bộ SENT/DELIVERED/READ |

Nên hỗ trợ receipt theo `lastMessageId` để batch và idempotent.

### 2.5 Account deletion lifecycle

| Method | Path đề xuất | Mục đích |
| --- | --- | --- |
| GET | `/api/v1/account/delete-request` | Xem trạng thái/lịch purge |
| DELETE | `/api/v1/account/delete-request` | Huỷ trong thời gian ân hạn |
| Internal job | purge worker | Hard-delete/anonymize theo policy |

## 3. API P1 nên bổ sung

### Saved posts

- `POST /api/v1/posts/{id}/save`
- `DELETE /api/v1/posts/{id}/save`
- `GET /api/v1/saved-posts?cursor=&limit=`

Nếu UC-11 chỉ là “lưu file về Photos”, API này không bắt buộc. Bảng `saved_posts` chỉ nên dùng khi sản phẩm có bookmark trong app.

### Notification preferences

- `GET /api/v1/notification-preferences`
- `PUT /api/v1/notification-preferences`

Cho phép bật/tắt `NEW_POST`, chat preview hoặc quiet hours; hiện use case nói “nếu bật” nhưng chưa có nơi lưu preference.

### Admin report operations

- `GET /api/v1/admin/reports?status=&targetType=&cursor=&limit=`
- `POST /api/v1/admin/reports/{id}/assign`
- `GET /api/v1/admin/reports/{id}/audit-log`

### AI job operations

- `DELETE /api/v1/ai/generations/{id}` hoặc `/cancel`
- `GET /api/v1/ai/usage`
- Webhook/SSE status thay cho polling nếu cần trải nghiệm nhanh.

## 4. Năng lực hạ tầng cần bổ sung

### Gateway

- Rate limit cho OTP, login, search, friend request và report.
- Request-size limit.
- Connect/response timeout.
- CORS allowlist cho admin web.
- Forward cùng một request ID xuống downstream; filter hiện tạo/trả header nhưng cần xác nhận propagation qua Gateway.
- Không retry request ghi dữ liệu nếu chưa có idempotency key.

### Async reliability

Domain transaction và notification đang tách qua REST. Nếu domain commit thành công nhưng call notification thất bại, cần:

- Outbox table theo service nguồn.
- Worker retry có backoff.
- Idempotency key theo event ID.
- Dead-letter/operational visibility.

Kafka chưa bắt buộc cho MVP; transactional outbox + worker REST đã đủ để tăng độ tin cậy.

### Production adapters

- OTP: SMS provider thật, anti-abuse, verify attempt counter.
- Push: FCM/APNs, token invalidation và delivery metrics.
- AI: provider/model, queue, quota, cost cap và safety moderation.
- Media: virus/type validation, thumbnail/transcode, signed/private access.
- Payment/email: chưa có adapter production hoặc operational runbook; giữ Icebox
  cho đến khi product scope và provider contract được duyệt.

## 5. Ưu tiên triển khai

1. Chuẩn hoá tài liệu và contract đang khác nhau.
2. Rate limit OTP/login và harden secret/config.
3. Media processing/private access và durable cleanup/notification observability (presigned lifecycle MVP đã có).
4. Account deletion worker/cancel/status để đáp ứng policy thật.
5. Push provider + outbox/idempotent retry.
6. Chat delivered receipt và broker bền vững.
7. Deep-link friend invite.
8. AI provider, quota và safety.
9. Admin pagination/audit/appeal.

## 6. Definition of Done cho API mới

- Contract OpenAPI và ví dụ request/response.
- Validate input và quyền domain.
- Idempotency cho thao tác có thể retry.
- Migration Flyway và ownership/grant đúng service.
- Unit/integration test cho happy path và lỗi chính.
- Chạy qua Gateway nếu là public API; không expose nếu là internal API.
- `ProblemDetail` nhất quán và có `X-Request-Id`.
- Có metrics/log nhưng không ghi token, OTP hoặc PII nhạy cảm.
- Swift client gọi được, có loading/empty/error/offline state phù hợp.


## Cập nhật triển khai 16/10/2026

Đã bổ sung cancel request (API + migration + Flutter), sửa OTP/social contract, secure token storage, refresh GETDEL, OTP cooldown/attempt limit và test routing độc lập mạng. CI có migration DB rỗng/validate và artifacts. Các bước Google OAuth credentials, demo Android/iOS, Terms gate trên API nghiệp vụ, concurrency block-vs-accept và review nhóm vẫn cần nghiệm thu; không coi unit test là end-to-end evidence.
