# Danh mục API

Base URL local qua Gateway: `http://localhost:8080`.

## 1. Contract chung

### Xác thực

```http
Authorization: Bearer <access-token>
X-Request-Id: <optional-client-request-id>
```

Public endpoint không cần token:

- `POST /api/v1/auth/google`
- `POST /api/v1/auth/phone/send-otp`
- `POST /api/v1/auth/phone/verify-otp`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/terms/latest`
- `POST /api/v1/admin/auth/login`

Access token của user có claim Boolean `terms_accepted`. Token cũ không có claim này được xem là chưa chấp nhận điều khoản. Gateway chỉ cho user chưa chấp nhận gọi nhóm `/api/v1/auth/**`, `GET /api/v1/terms/latest` và `POST /api/v1/terms/accept`; health, tài liệu API, internal API và admin API cũng được miễn kiểm tra này. Các API user khác trả về `403` với `code: TERMS_ACCEPTANCE_REQUIRED`.

Sau khi `POST /api/v1/terms/accept` thành công, client phải gọi `POST /api/v1/auth/refresh` và lưu cặp token mới trước khi chuyển vào luồng ứng dụng chính. Nếu refresh thất bại, client giữ người dùng ở màn hình Terms để thử lại.

### Pagination

Feed, message và notification dùng cursor:

```json
{
  "items": [],
  "nextCursor": "opaque-base64-cursor-or-null",
  "hasNext": false
}
```

Không parse hoặc tự tạo cursor ở client; chỉ gửi lại giá trị server trả về. `limit` được service giới hạn tối đa 50.

### Lỗi

```json
{
  "type": "https://loccoc.app/problems/validation_failed",
  "title": "VALIDATION_FAILED",
  "status": 400,
  "detail": "Request validation failed.",
  "code": "VALIDATION_FAILED",
  "requestId": "uuid",
  "fieldErrors": {
    "field": "validation message"
  }
}
```

Ví dụ khi access token chưa phản ánh bản Terms mới nhất:

```json
{
  "type": "https://loccoc.app/problems/terms_acceptance_required",
  "title": "TERMS_ACCEPTANCE_REQUIRED",
  "status": 403,
  "detail": "Accept the latest Terms of Service before using this endpoint.",
  "code": "TERMS_ACCEPTANCE_REQUIRED",
  "requestId": "uuid"
}
```

## 2. Identity API

| Method | Path | Auth | Input chính | Kết quả |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/auth/google` | Public | `{idToken}` | Access/refresh token, user, `requiresTermsAcceptance` |
| POST | `/api/v1/auth/phone/send-otp` | Public | `{phoneNumber}` | Message, TTL, `devOtp` nếu local |
| POST | `/api/v1/auth/phone/verify-otp` | Public | `{phoneNumber, otp, displayName?}` | Access/refresh token và user |
| POST | `/api/v1/auth/refresh` | Public | `{refreshToken}` | Cặp token mới |
| POST | `/api/v1/auth/logout` | Public | `{refreshToken}` | `204 No Content` |
| GET | `/api/v1/users/me` | User | — | Profile hiện tại |
| PUT | `/api/v1/users/me` | User | `{username?, displayName?, avatarUrl?, bio?}` | Profile đã cập nhật |
| GET | `/api/v1/terms/latest` | Public | — | `{id, version, contentUrl, publishedAt}` |
| POST | `/api/v1/terms/accept` | User | `{termsVersionId}` | `{termsVersionId, acceptedAt}` |
| POST | `/api/v1/account/delete-request` | User | — | `202`, request purge sau 30 ngày |

Ràng buộc đáng chú ý:

- Phone có 8–15 chữ số, có thể bắt đầu bằng `+`.
- OTP đúng 6 chữ số.
- Username 3–50 ký tự, chỉ chữ, số và `_`.
- Client phải chấp nhận đúng terms version mới nhất.

`AuthResponse`:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "user": {},
  "requiresTermsAcceptance": true
}
```

## 3. Social API

| Method | Path | Auth | Input | Kết quả |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/friends/search?keyword=` | User | keyword 1–100 ký tự | User kèm trạng thái friend/block/pending |
| GET | `/api/v1/friends` | User | — | Danh sách user summary |
| GET | `/api/v1/friends/ids` | User | — | Danh sách ID |
| POST | `/api/v1/friend-requests` | User | `{receiverId}` | `201`, pending request |
| GET | `/api/v1/friend-requests/incoming` | User | — | Request nhận được |
| GET | `/api/v1/friend-requests/outgoing` | User | — | Request đã gửi |
| POST | `/api/v1/friend-requests/{id}/accept` | User | — | Friendship |
| POST | `/api/v1/friend-requests/{id}/decline` | User | — | Request trạng thái `DECLINED` |
| POST | `/api/v1/friend-requests/{id}/cancel` | Sender | — | `CANCELLED`; retry cùng sender idempotent; 403 nếu không phải sender, 409 nếu đã accept/decline |
| POST | `/api/v1/blocks/{userId}` | User | — | `201`, block |
| DELETE | `/api/v1/blocks/{userId}` | User | — | `204` |
| GET | `/api/v1/blocks` | User | — | Danh sách block |

Quy tắc search: username/display name hỗ trợ tìm kiếm một phần. Số điện thoại chỉ
đối sánh chính xác sau khi chuẩn hóa (`090...`, `84...`, `+84...`) và băm SHA-256;
chuỗi số điện thoại chưa đầy đủ trả về danh sách rỗng, không chạy `LIKE` trên
`phone_number`. `phone_lookup_hash` là dữ liệu nội bộ và không xuất hiện trong DTO.

Search result:

```json
{
  "user": {
    "id": 2,
    "username": "friend",
    "displayName": "Friend",
    "avatarUrl": null
  },
  "friend": false,
  "blocked": false,
  "pendingRequestId": null
}
```

## 4. Post, media, feed và widget

| Method | Path | Auth | Input | Kết quả |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/media/upload` | User | multipart `file` | `201`, media metadata/URL |
| POST | `/api/v1/posts` | User | `CreatePostRequest` | `201`, post |
| GET | `/api/v1/posts/{id}` | User | — | Post nếu có quyền xem |
| GET | `/api/v1/feed?cursor=&limit=20` | User | cursor | Page post bạn bè |
| GET | `/api/v1/feed/by-friend/{friendId}` | User | cursor | Page post của một friend |
| GET | `/api/v1/posts/users/{userId}` | User | cursor | Page post theo user |
| GET | `/api/v1/widget/latest-post` | User | — | Post mới nhất cho widget |

`CreatePostRequest`:

```json
{
  "mediaType": "IMAGE",
  "mediaUrl": "https://...",
  "thumbnailUrl": "https://...",
  "mediaWidth": 1170,
  "mediaHeight": 2532,
  "fileSizeBytes": 123456,
  "durationSeconds": 0,
  "caption": "Hello",
  "filterApplied": "warm",
  "aiGenerated": false,
  "aiGenerationId": null,
  "visibility": "FRIENDS_ONLY"
}
```

Enums:

- `mediaType`: `IMAGE`, `GIF`.
- `visibility`: `PUBLIC`, `FRIENDS_ONLY`, `PRIVATE`.
- `durationSeconds`: 0–5.

## 5. Notification API

| Method | Path | Auth | Input | Kết quả |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/device-tokens` | User | `{token, platform}` | `201`, device token |
| DELETE | `/api/v1/device-tokens/{token}` | User | path token | `204` |
| GET | `/api/v1/notifications?cursor=&limit=20` | User | cursor | Page notification |
| POST | `/api/v1/notifications/{id}/read` | User | — | Notification đã đọc |
| POST | `/api/v1/notifications/read-all` | User | — | `{updated}` |

`platform`: `IOS`, `ANDROID`, `WEB`.

## 6. Chat REST và WebSocket

| Method | Path | Auth | Input | Kết quả |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/conversations` | User | — | Conversations của user |
| POST | `/api/v1/conversations/{friendId}` | User | — | `201`, mở/tạo conversation |
| GET | `/api/v1/conversations/{id}/messages?cursor=&limit=50` | User | cursor | Page message |
| POST | `/api/v1/messages` | User | `SendMessageRequest` | `201`, message |
| POST | `/api/v1/conversations/{id}/read` | User | — | `{updated}` |

`SendMessageRequest`, dùng chung cho REST và STOMP:

```json
{
  "conversationId": 10,
  "messageType": "TEXT",
  "content": "Xin chào",
  "mediaUrl": null,
  "thumbnailUrl": null
}
```

`messageType`: `TEXT`, `IMAGE`, `GIF`, `SYSTEM`.

WebSocket:

| Loại | Destination | Ý nghĩa |
| --- | --- | --- |
| Endpoint | `/ws/chat` | STOMP handshake |
| Send | `/app/chat.send` | Gửi `SendMessageRequest` |
| Subscribe | `/topic/conversations/{conversationId}` | Nhận `MessageResponse` |

## 7. AI API

| Method | Path | Auth | Input | Kết quả |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/ai/generations` | User | generation request | `202`, generation |
| GET | `/api/v1/ai/generations/{id}` | User | — | Trạng thái/kết quả |
| POST | `/api/v1/ai/generations/{id}/retry` | User | — | `202`, chạy lại |

```json
{
  "originalMediaUrl": "https://...",
  "stylePrompt": "anime style",
  "generationType": "IMAGE_TO_IMAGE"
}
```

`generationType`: `TEXT_TO_IMAGE`, `IMAGE_TO_IMAGE`, `FILTER_ENHANCE`.

`status`: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`.

## 8. Moderation API

| Method | Path | Auth | Input | Kết quả |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/reports` | User | report post hoặc user | `201`, report |
| POST | `/api/v1/admin/auth/login` | Public | `{username, password}` | Admin access token |
| GET | `/api/v1/admin/reports` | Admin | — | Tất cả report |
| GET | `/api/v1/admin/reports/{id}` | Admin | — | Chi tiết report |
| POST | `/api/v1/admin/reports/{id}/resolve` | Admin | `{action, resolutionNote?}` | Report đã đóng |
| POST | `/api/v1/admin/reports/{id}/reject` | Admin | `{resolutionNote?}` | Report `REJECTED` |

Report phải có đúng một trong `reportedPostId` hoặc `reportedUserId`:

```json
{
  "reportedPostId": 123,
  "reportedUserId": null,
  "reason": "Nội dung không phù hợp"
}
```

Action: `HIDE_POST`, `DELETE_POST`, `WARN_USER`, `BAN_USER`, `REJECT_REPORT`. `NONE` không phải final action hợp lệ.

## 9. Internal API

Tất cả endpoint dưới đây yêu cầu `X-Internal-Api-Key` và không đi qua Gateway.

### Identity internal

| Method | Path | Caller |
| --- | --- | --- |
| GET | `/internal/v1/users/search?keyword=` | social; text partial hoặc phone chính xác qua hash |
| POST | `/internal/v1/users/batch` body `{ids}` | social, post, moderation |
| POST | `/internal/v1/users/{id}/status` body `{status}` | moderation |

### Social internal

| Method | Path | Caller |
| --- | --- | --- |
| GET | `/internal/v1/friends/{userId}/ids` | post |
| GET | `/internal/v1/friends/check?userA=&userB=` | post, chat |
| GET | `/internal/v1/blocks/check?userA=&userB=` | post, chat |

### Post internal

| Method | Path | Caller |
| --- | --- | --- |
| GET | `/internal/v1/posts/{id}` | moderation |
| POST | `/internal/v1/posts/{id}/status` body `{status}` | moderation |

### Notification internal

| Method | Path | Caller |
| --- | --- | --- |
| POST | `/internal/v1/notifications/friend-request` | social |
| POST | `/internal/v1/notifications/friend-accept` | social |
| POST | `/internal/v1/notifications/new-post` | post |
| POST | `/internal/v1/notifications/chat-message` | chat |
| POST | `/internal/v1/notifications/report-result` | moderation |

Notification event:

```json
{
  "userId": 2,
  "actorUserId": 1,
  "referenceId": 99,
  "title": null,
  "body": null
}
```

Caller nên gửi `Idempotency-Key` ổn định theo domain event để tránh tạo notification trùng khi retry.


### Bổ sung Sprint 1 — 16/10/2026

- OTP mock trả `devOtp` khi expose-code bật; verify nhận `otp`, không phải `otpCode`.
- Gửi lại OTP: cooldown 60 giây, HTTP 429 `OTP_RATE_LIMITED`. Tối đa 5 lần thử trong cửa sổ TTL OTP; vượt ngưỡng trả `OTP_ATTEMPTS_EXCEEDED`. Redis Lua xác minh và tiêu thụ OTP atomically.
- Refresh dùng Redis GETDEL để chỉ một request đồng thời tiêu thụ được refresh token cũ. Logout thu hồi refresh token; access token hiện còn hiệu lực đến expiry.
- Search relationship trả `friend`, `blocked`, `pendingRequestId`.
- Hủy lời mời yêu cầu migration V3, chỉ sender được hủy PENDING; retry CANCELLED trả cùng kết quả. Accept/decline/cancel khóa hàng request trong transaction.
