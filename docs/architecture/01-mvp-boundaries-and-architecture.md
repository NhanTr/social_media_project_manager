# LocCoc MVP Architecture and Bounded Contexts

Tài liệu thiết kế kiến trúc theo thẻ Trello **`LC-S0-02`** — Chốt MVP và kiến trúc triển khai cho nhóm ba người.

## 1. Quyết định kiến trúc chính (ADR)

1. **Monorepo**: Cấu trúc gồm `backend/` (Maven multi-module) và `loccoc_application/` (Flutter mobile app).
2. **Giao tiếp liên dịch vụ**: Giao tiếp nội bộ sử dụng REST đồng bộ với Header `X-Internal-Api-Key`. Không dùng Kafka/RabbitMQ trong MVP để giảm thiểu độ phức tạp vận hành.
3. **Cơ sở dữ liệu**: Dùng một cụm PostgreSQL với schema riêng biệt cho từng service theo nguyên tắc data ownership:
   - `identity_service`: `users`, `terms_versions`, `user_terms_acceptance`, `account_deletion_requests`
   - `social_service`: `friend_requests`, `friendships`, `blocks`
   - `post_service`: `posts`, `saved_posts`, `media`
   - `notification_service`: `notifications`, `device_tokens`
   - `chat_service`: `conversations`, `messages`
   - `ai_service`: `ai_generations`
   - `moderation_service`: `reports`, `admins`
4. **Bộ nhớ đệm & Realtime**:
   - Redis: Quản lý OTP mock, blacklist token / refresh token session, presence tạm thời.
   - WebSocket (STOMP): Nhắn tin realtime trong `chat-service`.
5. **Object Storage**: MinIO (S3-compatible) cho hình ảnh và media.
6. **Bảo mật**:
   - Gateway (cổng 8080) là điểm tiếp nhận duy nhất cho Mobile client.
   - JWT Access Token (HS256) chứa thông tin định danh và quyền.
   - Endpoint nội bộ `/internal/**` chặn từ Gateway, chỉ cho phép giao tiếp giữa các container với internal key.

## 2. Danh mục ngoài phạm vi MVP (Deferred / Icebox)

- Khám phá cộng đồng, Public Feed, Recommendation AI phức tạp.
- Group chat, cuộc gọi thoại / video.
- Home screen widget và GIF động nặng.
- Tích hợp SMS OTP nhà mạng thật (dùng OTP mock trong MVP).
- Tích hợp AI provider trả phí (dùng Fake AI Generator Worker).
- Service Mesh (Istio), Kubernetes, Kafka.
