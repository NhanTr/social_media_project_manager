# Luồng hoạt động

## 1. Đăng nhập bằng số điện thoại và chấp nhận điều khoản

Áp dụng UC-01, UC-02, UC-18.

```mermaid
sequenceDiagram
    actor U as User
    participant A as Swift App
    participant G as Gateway
    participant I as identity-service
    participant R as Redis
    participant D as PostgreSQL

    U->>A: Nhập số điện thoại
    A->>G: POST /auth/phone/send-otp
    G->>I: Forward public request
    I->>R: Lưu OTP với TTL
    I-->>A: expiresInSeconds, devOtp(local)
    U->>A: Nhập OTP
    A->>G: POST /auth/phone/verify-otp
    G->>I: Verify
    I->>R: Kiểm tra và xoá OTP
    I->>D: Tạo hoặc cập nhật user
    I->>R: Tạo refresh-token session
    I-->>A: accessToken, refreshToken, requiresTermsAcceptance
    alt Chưa chấp nhận điều khoản mới nhất
        A->>G: GET /terms/latest
        A->>G: POST /terms/accept
        G->>I: Lưu acceptance
        I->>D: Insert user_terms_acceptance
    end
    A->>G: GET /users/me
    G-->>A: User profile
```

Ngoại lệ chính:

- OTP sai/hết hạn: `INVALID_OTP` hoặc `OTP_EXPIRED`.
- Phone/OTP sai format: `VALIDATION_FAILED`.
- Terms version cũ: `TERMS_VERSION_OUTDATED`.
- Production phải rate-limit theo IP, phone và device; adapter SMS hiện vẫn là mock/local.

Google login gọi `POST /auth/google` với `idToken`, sau đó dùng chung nhánh tạo user, cấp token và kiểm tra terms.

## 2. Tìm bạn, gửi và xử lý lời mời

Áp dụng UC-03, UC-04.

```mermaid
sequenceDiagram
    participant A as User A App
    participant G as Gateway
    participant S as social-service
    participant I as identity-service
    participant N as notification-service
    participant B as User B App

    A->>G: GET /friends/search?keyword=...
    G->>S: Search
    S->>I: GET /internal/v1/users/search
    I-->>S: User summaries
    S-->>A: user + friend/blocked/pending flags
    A->>G: POST /friend-requests {receiverId}
    G->>S: Create pending request
    S->>S: Check self, block, friendship, duplicate
    S->>N: POST /internal/v1/notifications/friend-request
    N-->>B: In-app notification / push mock
    B->>G: POST /friend-requests/{id}/accept
    G->>S: Accept
    S->>S: Create normalized friendship pair
    S->>N: friend-accept notification
```

Decline đổi request sang `DECLINED`. Block sẽ xoá friendship hiện có, đóng pending request hai chiều và ngăn feed/chat/lời mời.

Deep-link mời bạn có trong use case Notion nhưng backend chưa có API phát hành/resolve invite link; xem [khoảng trống](05-gaps-and-roadmap.md).

## 3. Upload media, tạo post, feed và widget

Áp dụng UC-05, UC-06, UC-08 đến UC-12, UC-20.

```mermaid
sequenceDiagram
    participant A as Swift App
    participant G as Gateway
    participant P as post-service
    participant O as MinIO
    participant S as social-service
    participant I as identity-service
    participant N as notification-service

    A->>G: POST /media/upload (multipart file)
    G->>P: Forward media
    P->>O: Put object
    O-->>P: Object URL
    P-->>A: mediaType, mediaUrl, size, duration
    A->>G: POST /posts (metadata + mediaUrl)
    G->>P: Create post
    P->>S: GET friend IDs
    P->>N: NEW_POST cho từng friend
    P-->>A: PostResponse

    A->>G: GET /feed?cursor=&limit=
    P->>S: Lấy friend IDs
    P->>P: Query ACTIVE + FRIENDS_ONLY
    P->>I: Batch author summaries
    P-->>A: items, nextCursor, hasNext
```

Quy tắc:

- `IMAGE`: JPEG/PNG tối đa 10 MB.
- `GIF`: tối đa 20 MB và `durationSeconds <= 5`.
- Visibility mặc định do service xử lý; feed chỉ trả nội dung user được quyền xem và không bị block.
- Filter ảnh và lưu vào Photos là client-side; backend chỉ lưu `filterApplied` và cung cấp media URL.
- Widget gọi `GET /widget/latest-post` để lấy payload nhỏ gọn.

Hiện file upload đi xuyên qua Gateway. Thiết kế production nên chuyển sang presigned URL để mobile upload trực tiếp object storage.

## 4. Chat realtime và trạng thái đọc

Áp dụng UC-13.

```mermaid
sequenceDiagram
    participant A as User A
    participant C as chat-service
    participant S as social-service
    participant N as notification-service
    participant B as User B

    A->>C: POST /conversations/{friendId}
    C->>S: Check friendship + block
    C-->>A: Conversation
    A->>C: STOMP CONNECT Authorization Bearer
    B->>C: STOMP CONNECT + SUBSCRIBE topic
    A->>C: SEND /app/chat.send
    C->>C: Validate participant, persist SENT
    C-->>B: /topic/conversations/{id}
    alt B offline
        C->>N: CHAT_MESSAGE notification
    end
    B->>C: POST /conversations/{id}/read
    C->>C: Update messages to READ
```

Code có enum `SENT`, `DELIVERED`, `READ`, nhưng luồng cập nhật `DELIVERED` riêng chưa được expose thành API/receipt. Đây là chức năng cần bổ sung nếu UI phải hiển thị chính xác ba trạng thái.

## 5. Notification

Áp dụng UC-19.

Trigger hiện có:

- `FRIEND_REQUEST`
- `FRIEND_ACCEPT`
- `NEW_POST`
- `CHAT_MESSAGE`
- `REPORT_RESULT`

Service nguồn gọi `/internal/v1/notifications/{type}`. Notification service lưu record, dùng `Idempotency-Key` nếu caller cung cấp, sau đó adapter push hiện chỉ log mock. Mobile lấy danh sách bằng cursor, mark một notification hoặc toàn bộ là đã đọc.

## 6. AI generation

Áp dụng UC-07.

```mermaid
sequenceDiagram
    participant A as Swift App
    participant G as Gateway
    participant AI as ai-service
    participant D as PostgreSQL

    A->>G: POST /ai/generations
    G->>AI: Create
    AI->>D: PENDING
    AI-->>A: 202 + generation
    AI->>AI: Mock async processing
    AI->>D: PROCESSING -> SUCCESS/FAILED
    loop Poll
        A->>G: GET /ai/generations/{id}
        G-->>A: Current status/result
    end
    opt FAILED
        A->>G: POST /ai/generations/{id}/retry
    end
```

Provider AI và job queue production chưa được tích hợp. Nếu AI lỗi, client vẫn có thể dùng ảnh gốc để tạo post.

## 7. Report và moderation

Áp dụng UC-14, UC-15.

```mermaid
sequenceDiagram
    participant U as User
    participant M as moderation-service
    participant P as post-service
    participant I as identity-service
    participant N as notification-service
    participant A as Admin

    U->>M: POST /reports
    M->>P: Validate post (nếu report post)
    M->>I: Validate user (nếu report user)
    M-->>U: PENDING report
    A->>M: POST /admin/auth/login
    A->>M: GET /admin/reports
    A->>M: POST /admin/reports/{id}/resolve
    alt HIDE_POST hoặc DELETE_POST
        M->>P: POST /internal/v1/posts/{id}/status
    else BAN_USER
        M->>I: POST /internal/v1/users/{id}/status
    end
    M->>N: REPORT_RESULT
```

`WARN_USER` hiện đóng report nhưng chưa có kênh cảnh báo riêng ngoài kết quả report. Audit log từng thao tác moderation cũng chưa có trong MVP.

## 8. Xoá tài khoản

Áp dụng UC-17.

1. User gọi `POST /account/delete-request`.
2. Identity service từ chối nếu đã có request `PENDING`.
3. User chuyển sang `INACTIVE`.
4. Tạo lịch purge sau 30 ngày.
5. Xoá refresh-token sessions trong Redis.

Worker hard-delete, API huỷ yêu cầu trong thời gian ân hạn và trạng thái purge thực tế chưa có.
