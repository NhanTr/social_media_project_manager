# Chức năng từng service

## 1. API Gateway

Trách nhiệm:

- Cửa vào duy nhất cho REST public và route WebSocket.
- Định tuyến theo path.
- Kiểm tra JWT sớm.
- Public endpoint allowlist.
- Gắn/trả `X-Request-Id`.
- Expose health/route metrics cơ bản.

Không chịu trách nhiệm:

- Không phát hành token.
- Không chứa business validation.
- Không truy cập PostgreSQL.
- Không được expose internal API.

Hiện trạng cần lưu ý: gateway dùng Web MVC, chưa thấy rate limiting, request-size filter, timeout/circuit breaker hoặc CORS policy production rõ ràng.

## 2. identity-service

Sở hữu: `users`, `terms_versions`, `user_terms_acceptance`, `account_deletion_requests`; OTP và refresh session trong Redis.

Chức năng:

- Google Sign-In và phone OTP.
- Tạo access/refresh token, refresh rotation và logout.
- Profile của user.
- Phiên bản điều khoản và log chấp nhận.
- Soft-deactivate khi yêu cầu xoá tài khoản.
- Internal user search/batch lookup.
- Internal ban/inactivate user cho moderation.

Phụ thuộc: PostgreSQL, Redis, Google token verifier.

Chưa hoàn chỉnh production: SMS provider, API huỷ deletion, worker purge, key rotation/asymmetric JWT.

## 3. social-service

Sở hữu: `friend_requests`, `friendships`, `blocks`.

Chức năng:

- Tìm user thông qua identity-service.
- Gửi, nhận, accept, decline friend request.
- Chuẩn hoá friendship thành cặp `(minUserId, maxUserId)`.
- Block/unblock.
- Khi block: xoá friendship và đóng pending requests.
- Cung cấp internal check friendship/block cho post và chat.
- Gọi notification-service khi request/accept.

Phụ thuộc: identity-service, notification-service, PostgreSQL.

Không chịu trách nhiệm: profile user, nội dung post, gửi push trực tiếp.

## 4. post-service

Sở hữu: `posts`, `saved_posts`; media object trong MinIO.

Chức năng:

- Validate và upload JPEG/PNG/GIF.
- Tạo post với media metadata, caption, filter và AI reference.
- Kiểm tra visibility/relationship trước khi trả post.
- Feed bạn bè bằng cursor.
- Lọc feed theo friend và list post theo user.
- Payload latest post cho widget.
- Internal read/change post status cho moderation.
- Tạo `NEW_POST` notification.

Phụ thuộc: social-service, identity-service, notification-service, PostgreSQL, MinIO.

Chưa hoàn chỉnh production: presigned upload, media processing/thumbnail pipeline, API xoá/sửa post của owner, saved-post API.

## 5. notification-service

Sở hữu: `notifications`, `device_tokens`.

Chức năng:

- Đăng ký và vô hiệu hoá device token.
- Lưu/list/mark-read notification.
- Nhận domain event nội bộ từ social, post, chat và moderation.
- Hỗ trợ idempotency key cho notification.
- Chuẩn bị title/body mặc định theo notification type.

Phụ thuộc: PostgreSQL; FCM/APNs adapter trong tương lai.

Hiện trạng: push chỉ là mock logger. Việc notification không gửi được không nên rollback nghiệp vụ nguồn; các caller hiện xử lý theo hướng best-effort ở một số luồng.

## 6. chat-service

Sở hữu: `conversations`, `messages`.

Chức năng:

- Mở/list conversation 1-1.
- Kiểm tra chỉ bạn bè không bị block mới được chat.
- Lưu và phân trang lịch sử message.
- Gửi bằng REST hoặc STOMP.
- Broadcast message theo conversation topic.
- Kiểm tra quyền subscribe topic.
- Mark message là `READ`.
- Nếu receiver offline, tạo `CHAT_MESSAGE` notification.

Phụ thuộc: social-service, notification-service, PostgreSQL, STOMP simple broker.

Chưa hoàn chỉnh production: delivery receipt thực sự, broker ngoài tiến trình, reconnect/replay, attachment lifecycle và anti-spam.

## 7. ai-service

Sở hữu: `ai_generations`.

Chức năng:

- Tạo generation job.
- Theo dõi trạng thái `PENDING -> PROCESSING -> SUCCESS/FAILED`.
- Chỉ owner được đọc/retry job.
- Hỗ trợ text-to-image, image-to-image và filter enhance.

Phụ thuộc: PostgreSQL; provider/model và queue trong tương lai.

Hiện trạng: worker và output là mock. Production cần adapter provider, quota/cost control, content safety, timeout/cancellation và durable queue.

## 8. moderation-service

Sở hữu: `reports`, `admins`.

Chức năng:

- User report đúng một post hoặc user.
- Admin login và role-based access.
- List/get/resolve/reject report.
- Hide/delete post qua post internal API.
- Ban user qua identity internal API.
- Gửi kết quả xử lý tới reporter.

Phụ thuộc: identity-service, post-service, notification-service, PostgreSQL.

Chưa hoàn chỉnh production: audit trail, pagination/filter admin, evidence snapshot, warning workflow, appeal và fine-grained permission.

## 9. common-lib

Chức năng dùng chung:

- JWT parsing/validation và current user principal.
- Bảo vệ public/internal endpoint.
- `ApiException` và `ProblemDetail` error contract.
- `PageResponse`.
- `X-Request-Id` filter.

Quy tắc: common-lib chỉ chứa cross-cutting contract/utility; không đưa entity hoặc business logic của một domain vào đây.

## 10. Ma trận phụ thuộc

| Caller | Identity | Social | Post | Notification |
| --- | ---: | ---: | ---: | ---: |
| social-service | Search/batch user | — | — | Friend events |
| post-service | Author summaries | Friend IDs/check/block | — | New post |
| chat-service | — | Friend/block check | — | Offline chat |
| moderation-service | User check/ban | — | Post check/status | Report result |

Dependency hiện là đồng bộ. Nếu một dependency không sẵn sàng:

- Validation bắt buộc như friendship, post target hoặc user target nên fail request.
- Side effect best-effort như push/notification có thể log và retry bất đồng bộ.
- Không tự retry POST ở Gateway nếu chưa có idempotency.
