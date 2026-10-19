# Kế hoạch triển khai LocCoc cho nhóm ba thành viên

> **Trạng thái dự án:** Mới bắt đầu - chưa triển khai hạng mục kỹ thuật nào.
>
> **Tiến độ sản phẩm:** 0/24 task hoàn thành.
>
> **Cập nhật trạng thái:** 23/09/2026.

## Nhật ký công việc

- [x] Chuyển kế hoạch từ `LocCoc_Project_Plan_3_Members.docx` sang Markdown để tiếp tục làm việc về sau.
- [ ] Bắt đầu triển khai dự án.

## Việc cần làm tiếp theo

1. Thực hiện `LC-S0-01` để kiểm kê repository và tạo baseline GitHub.
2. Chỉ đánh dấu task hoàn thành khi đáp ứng đủ tiêu chí nghiệm thu và có bằng chứng kiểm thử trong kế hoạch này.
3. Sau mỗi phiên làm việc, cập nhật checklist tiến độ và nhật ký công việc trong file này.

## Checklist tiến độ kỹ thuật

### Sprint 0

- [ ] LC-S0-01 Kiểm kê repository và tạo baseline GitHub
- [ ] LC-S0-02 Chốt MVP và kiến trúc triển khai cho nhóm ba người
- [ ] LC-S0-03 Khởi tạo Flutter và khung backend dùng chung
- [ ] LC-S0-04 Thiết lập CI chất lượng và quy trình Git

### Sprint 1

- [ ] LC-S1-01 User Service Auth Profile và Terms
- [ ] LC-S1-02 Flutter Auth Session và App Shell
- [ ] LC-S1-03 User Service Friend Request Friendship và Block
- [ ] LC-S1-04 Flutter Friends Requests và Block UI

### Sprint 2

- [ ] LC-S2-01 Post Service Media Post và Friends Only Feed
- [ ] LC-S2-02 Flutter Capture Composer và Upload
- [ ] LC-S2-03 Flutter Feed Filter và Media Detail
- [ ] LC-S2-04 Post Service AI Generation Mock và entitlement hook

### Sprint 3

- [ ] LC-S3-01 Chat Service Conversation Message và Realtime
- [ ] LC-S3-02 Flutter Conversation Chat và Offline Queue
- [ ] LC-S3-03 Notification Service In App Push Adapter và Email
- [ ] LC-S3-04 Flutter Notification Center và Deep Link

### Sprint 4

- [ ] LC-S4-01 Subscription Service Tier và Entitlement
- [ ] LC-S4-02 Payment Sandbox Checkout và Webhook
- [ ] LC-S4-03 Flutter Paywall và Quản lý Tier
- [ ] LC-S4-04 Admin Service Report và Moderation

### Sprint 5

- [ ] LC-S5-01 Observability Security và Data Retention
- [ ] LC-S5-02 Automated Contract Integration và End to End Tests
- [ ] LC-S5-03 Staging và Flutter Beta Release
- [ ] LC-S5-04 Tài liệu vận hành Demo và Bàn giao

## Nội dung kế hoạch gốc

**Flutter và sáu dịch vụ backend cho MVP 2026**

*Ngày lập kế hoạch 23 tháng 9 năm 2026<br>Nguồn đối chiếu Notion LocCoc và Trello Project Management*

**Quyết định chính.** Phạm vi được thu gọn thành một Flutter app và sáu bounded service trong monorepo. Nhóm không triển khai Kafka, service mesh, database riêng từng service, AI provider thật hoặc payment production trong MVP. Kế hoạch kéo dài sáu sprint và ưu tiên một vertical slice có thể demo sau từng sprint.

**Cảnh báo baseline.** Repository GitHub gắn trên Trello hiện không trả về branch hoặc commit. Notion lại ghi backend hoàn thành 100 phần trăm vào ngày 23 tháng 7 năm 2026. Vì chưa có bằng chứng Git để đối chiếu, kế hoạch không đánh dấu hạng mục kỹ thuật nào là hoàn thành và đặt việc khôi phục baseline lên đầu Sprint 0.

### Phạm vi MVP

MVP giữ các miền nghiệp vụ người dùng yêu cầu, nhưng giảm số công nghệ và số luồng phụ để ba thành viên có thể phát hành bản beta trong khoảng mười một tuần.

| Dịch vụ | Trách nhiệm MVP | Dữ liệu sở hữu | Phần hoãn |
| --- | --- | --- | --- |
| User Service | Auth, profile, Terms, friend request, friendship, block | users, sessions, terms, friend_requests, friendships, blocks | SMS production, invite attribution nâng cao |
| Post Service | Media, post, feed, filter theo bạn, AI generation mock | posts, media, ai_generations | Video hoặc GIF pipeline, AI provider thật |
| Chat Service | Conversation một-một, message, WebSocket, receipt | conversations, messages | Group chat, attachment nâng cao |
| Notification Service | In-app inbox, device token, email và push adapter | notifications, device_tokens, deliveries | Chiến dịch marketing, provider production |
| Subscription Service | Tier, entitlement, checkout sandbox, webhook | tiers, subscriptions, payments, webhook_events | Thuế, hóa đơn, nhiều cổng thanh toán |
| Admin Service | Report queue, moderation action, audit | reports, moderation_actions | Dashboard phân tích và rule engine |

### Kiến trúc thu gọn

- Monorepo gồm apps/mobile_flutter, gateway và sáu service. Mỗi service vẫn có module, migration và API contract riêng để có thể tách deployment sau.
- Giao tiếp nội bộ dùng REST đồng bộ. Chat dùng WebSocket. Không dùng message broker trong MVP; tác vụ retry dùng bảng outbox hoặc job scheduler đơn giản khi cần.
- Một cụm PostgreSQL dùng schema riêng theo service. Redis phục vụ OTP mock, token revocation, rate limit và presence tạm thời. Object storage giữ media.
- Gateway xác thực JWT cho API public. API nội bộ dùng network policy và internal credential; không được expose qua route công khai.
- AI và push dùng adapter mock. Payment dùng sandbox; webhook mới là nguồn sự thật của tier.
### Ngoài phạm vi MVP

- Public feed, reaction, recommendation và social discovery
- Group chat và gọi thoại hoặc video
- Home screen widget và GIF 5 giây
- AI provider thật trước khi có ngân sách và quota
- SMS OTP production, push production và nhiều payment gateway
- Service mesh, Kafka, Kubernetes và database cluster riêng cho từng service
### Mô hình nhóm ba thành viên

| Vai trò | Trọng tâm | Trách nhiệm xuyên suốt | Không làm một mình |
| --- | --- | --- | --- |
| Thành viên A | Flutter | UI, state, offline, integration test, beta build | API contract và release approval |
| Thành viên B | Core backend | User, Post, Subscription, DB migration | Security review và payment review |
| Thành viên C | Realtime and platform | Chat, Notification, Admin, Gateway, CI and deploy | Mobile deep link và moderation policy |

Quy tắc tải việc: mỗi người tối đa một task chính đang làm. Pull request phải có ít nhất một người khác review. Công việc chạm auth, payment, block hoặc moderation cần review chéo bắt buộc.

### Roadmap sáu sprint

| Sprint | Thời gian | Mục tiêu | Điểm demo |
| --- | --- | --- | --- |
| Sprint 0 Nền tảng và baseline | 23/09/2026 đến 27/09/2026 | Xác minh code hiện có, chốt kiến trúc thu gọn và tạo đường chạy chung cho Flutter cùng sáu dịch vụ backend. | Flutter gọi Gateway và toàn hệ thống chạy local |
| Sprint 1 User Service và khung Flutter | 28/09/2026 đến 11/10/2026 | Người dùng đăng nhập, duy trì phiên, đồng ý điều khoản, quản lý hồ sơ và thiết lập quan hệ bạn bè hoặc block. | Hai user đăng nhập, kết bạn và block |
| Sprint 2 Post Service và trải nghiệm nội dung | 12/10/2026 đến 25/10/2026 | Người dùng tạo bài ảnh, xem feed bạn bè, lọc theo bạn và thử AI generation bằng mock không phát sinh chi phí thật. | Chụp hoặc chọn ảnh, đăng và xem feed theo bạn |
| Sprint 3 Chat và Notification | 26/10/2026 đến 08/11/2026 | Bạn bè nhắn tin realtime với trạng thái cơ bản; thông báo in-app và email hoạt động qua adapter thay thế được. | Hai user chat realtime và nhận notification |
| Sprint 4 Subscription và Admin | 09/11/2026 đến 22/11/2026 | Tài khoản có tier Free và Plus, payment sandbox cập nhật entitlement an toàn, report được xử lý qua Admin Service. | Nâng tier sandbox và admin xử lý report |
| Sprint 5 Hardening và phát hành MVP | 23/11/2026 đến 06/12/2026 | Hoàn thiện luồng end-to-end, bảo mật, quan sát hệ thống và phát hành bản thử nghiệm cho Android cùng iOS. | Beta Android and iOS chạy trên staging |

### Backlog chi tiết theo sprint

Mỗi task dưới đây là một Trello card. ID task phải xuất hiện trong branch, pull request hoặc commit để có thể truy ngược tiến độ từ GitHub.

### Sprint 0 Nền tảng và baseline

23/09/2026 đến 27/09/2026. Xác minh code hiện có, chốt kiến trúc thu gọn và tạo đường chạy chung cho Flutter cùng sáu dịch vụ backend.

#### LC-S0-01 Kiểm kê repository và tạo baseline GitHub

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên B | 2 ngày | Không |

**Kết quả cần đạt.** Repository có branch, commit và tag baseline phản ánh đúng code backend đang tồn tại; không tiếp tục dựa trên trạng thái 100 phần trăm chỉ có trong Notion.

**Phạm vi triển khai**

- Xác định vị trí source backend đã được tracker Notion ghi nhận
- Đưa source hợp lệ vào repository GitHub
- Tạo main và develop, bảo vệ main bằng pull request
- Gắn tag backend-baseline-v0.1.0
- Lập bảng commit-to-feature cho các service đã có
**Tiêu chí nghiệm thu**

- git ls-remote trả về ít nhất main và develop
- Tag baseline trỏ tới commit build được
- README ghi cách chạy và danh sách service
- Mọi thay đổi chưa xác minh được ghi là chưa hoàn thành
**Bằng chứng kiểm thử.** Build toàn repository; Chạy kiểm tra cấu hình Docker Compose; Đối chiếu commit với tracker Notion.

**Commit gợi ý.** chore(repo): publish verified backend baseline

#### LC-S0-02 Chốt MVP và kiến trúc triển khai cho nhóm ba người

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Cả nhóm | 1 ngày | LC-S0-01 |

**Kết quả cần đạt.** Đội có một quyết định kiến trúc duy nhất, giữ ranh giới sáu dịch vụ nhưng giảm chi phí vận hành và giao tiếp nội bộ.

**Phạm vi triển khai**

- Chốt Flutter cho Android và iOS
- Chốt User, Post, Chat, Notification, Subscription và Admin Service
- Dùng monorepo và REST đồng bộ trong MVP
- Dùng một PostgreSQL với schema riêng theo service
- Dùng Redis cho OTP, token và trạng thái realtime
- Hoãn Kafka, service mesh và database riêng từng service
**Tiêu chí nghiệm thu**

- Có sơ đồ context và dependency
- Mỗi bảng dữ liệu có service sở hữu
- Mỗi API public hoặc internal có quy tắc xác thực
- Danh sách ngoài phạm vi được cả nhóm đồng ý
**Bằng chứng kiểm thử.** Review chéo giữa ba thành viên; Walkthrough một luồng auth đến post và chat.

**Commit gợi ý.** docs(architecture): define three-member MVP boundaries

#### LC-S0-03 Khởi tạo Flutter và khung backend dùng chung

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên A và B | 3 ngày | LC-S0-02 |

**Kết quả cần đạt.** Flutter gọi được health endpoint qua Gateway; sáu service build và chạy bằng một lệnh local.

**Phạm vi triển khai**

- Tạo Flutter app với feature-first structure
- Tạo networking, secure storage, routing và environment config
- Tạo API Gateway và sáu service skeleton
- Tạo common error envelope, request ID và auth middleware
- Tạo PostgreSQL, Redis và object storage trong Docker Compose
**Tiêu chí nghiệm thu**

- Flutter chạy trên Android emulator và iOS simulator
- Gateway và sáu service báo healthy
- App gọi health endpoint thành công
- Không có secret thật trong repository
**Bằng chứng kiểm thử.** flutter analyze; Flutter smoke test; Backend unit or context test; docker compose health check.

**Commit gợi ý.** feat(platform): bootstrap flutter and service skeletons

#### LC-S0-04 Thiết lập CI chất lượng và quy trình Git

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên C | 2 ngày | LC-S0-01, LC-S0-03 |

**Kết quả cần đạt.** Mỗi pull request tự động kiểm tra Flutter, backend, migration và lỗi định dạng trước khi merge.

**Phạm vi triển khai**

- Định nghĩa branch và pull request workflow
- Chạy Flutter format, analyze và test
- Chạy backend build, unit test và migration check
- Chạy secret scan và dependency audit cơ bản
- Tạo pull request template liên kết Trello và commit
**Tiêu chí nghiệm thu**

- Pull request lỗi không được merge
- Template yêu cầu Trello card, test evidence và rollback note
- Artifacts test được giữ lại
- Có quy ước Conventional Commits
**Bằng chứng kiểm thử.** Tạo pull request mẫu pass; Tạo pull request cố ý lỗi và xác nhận bị chặn.

**Commit gợi ý.** ci: add flutter backend and migration quality gates

### Sprint 1 User Service và khung Flutter

28/09/2026 đến 11/10/2026. Người dùng đăng nhập, duy trì phiên, đồng ý điều khoản, quản lý hồ sơ và thiết lập quan hệ bạn bè hoặc block.

#### LC-S1-01 User Service Auth Profile và Terms

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên B | 4 ngày | LC-S0-03 |

**Kết quả cần đạt.** User Service hỗ trợ Google login và OTP mock, JWT access and refresh, profile và lưu phiên bản điều khoản đã đồng ý.

**Phạm vi triển khai**

- Google token verification adapter
- OTP mock có giới hạn gửi lại và hết hạn
- Access token, refresh rotation và revoke
- GET and PATCH profile
- Terms version và acceptance log
- Soft delete request ở mức tối thiểu
**Tiêu chí nghiệm thu**

- User mới phải đồng ý Terms trước khi dùng app
- Refresh token dùng lại bị từ chối
- Logout thu hồi phiên
- Profile chỉ sửa field cho phép
- API có validation và mã lỗi ổn định
**Bằng chứng kiểm thử.** Unit test token lifecycle; Integration test login refresh logout; Migration test từ database rỗng.

**Commit gợi ý.** feat(user): add auth profile terms and session lifecycle

#### LC-S1-02 Flutter Auth Session và App Shell

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên A | 4 ngày | LC-S1-01 |

**Kết quả cần đạt.** Ứng dụng có luồng mở app, đăng nhập, Terms, refresh phiên, logout và điều hướng chính ổn định.

**Phạm vi triển khai**

- Splash and session bootstrap
- Google sign-in UI và OTP mock UI
- Terms consent
- Secure token storage
- Automatic refresh with single-flight lock
- Main navigation shell và global error handling
**Tiêu chí nghiệm thu**

- Cold start đưa người dùng đến đúng màn hình
- Token không xuất hiện trong log
- 401 chỉ retry một lần
- Logout xóa local session
- Có loading empty error states
**Bằng chứng kiểm thử.** Widget test auth states; Integration test login to home; Kiểm thử offline và token hết hạn.

**Commit gợi ý.** feat(flutter-auth): implement session and navigation shell

#### LC-S1-03 User Service Friend Request Friendship và Block

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên B | 4 ngày | LC-S1-01 |

**Kết quả cần đạt.** Hai tài khoản có thể tìm nhau, gửi và xử lý lời mời; block được áp dụng nhất quán cho post và chat.

**Phạm vi triển khai**

- Search username hoặc phone hash
- Send accept decline cancel friend request
- List friends and pending requests
- Block and unblock
- Internal authorization endpoint cho Post và Chat
- Unique constraints chống duplicate and self relation
**Tiêu chí nghiệm thu**

- Không tự kết bạn hoặc tạo request trùng
- Block hủy friendship và request đang chờ
- Blocked pair không xem bài hoặc chat
- Mọi mutation idempotent theo nghiệp vụ
**Bằng chứng kiểm thử.** Unit test state transitions; Integration test concurrent friend request; Contract test internal relationship check.

**Commit gợi ý.** feat(user): add friendship requests and blocking rules

#### LC-S1-04 Flutter Friends Requests và Block UI

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên A | 3 ngày | LC-S1-03 |

**Kết quả cần đạt.** Người dùng tìm bạn, xử lý lời mời, xem danh sách bạn và block hoặc unblock từ ứng dụng.

**Phạm vi triển khai**

- Friend search with debounce
- Incoming and outgoing requests
- Accept decline cancel actions
- Friends list and profile preview
- Block confirmation and unblock
- Optimistic update có rollback
**Tiêu chí nghiệm thu**

- UI phản ánh đúng pending friend blocked
- Không gửi thao tác trùng khi double tap
- Blocked user biến mất khỏi surface liên quan
- Lỗi mạng cho phép thử lại
**Bằng chứng kiểm thử.** Widget tests; Flutter integration test hai tài khoản; Accessibility labels cho action chính.

**Commit gợi ý.** feat(flutter-social): add friends requests and block flows

### Sprint 2 Post Service và trải nghiệm nội dung

12/10/2026 đến 25/10/2026. Người dùng tạo bài ảnh, xem feed bạn bè, lọc theo bạn và thử AI generation bằng mock không phát sinh chi phí thật.

#### LC-S2-01 Post Service Media Post và Friends Only Feed

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên B | 5 ngày | LC-S1-03 |

**Kết quả cần đạt.** Post Service quản lý upload, metadata, bài đăng và feed friends-only có phân trang.

**Phạm vi triển khai**

- Presigned upload and finalize
- Create update delete post
- Image validation and metadata
- Friends-only authorization qua User Service
- Cursor pagination and stable sorting
- Soft delete media cleanup job
**Tiêu chí nghiệm thu**

- Non-friend không đọc được post
- Upload chưa finalize tự hết hạn
- Feed không trùng hoặc bỏ bài khi phân trang
- Delete ẩn bài ngay và lên lịch xóa media
**Bằng chứng kiểm thử.** Unit test authorization; Integration test upload and feed; Contract test User Service; Giới hạn kích thước and MIME.

**Commit gợi ý.** feat(post): add media posts and friends-only feed

#### LC-S2-02 Flutter Capture Composer và Upload

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên A | 5 ngày | LC-S2-01 |

**Kết quả cần đạt.** Người dùng chọn hoặc chụp ảnh, áp filter cục bộ, thêm caption và đăng với tiến trình upload rõ ràng.

**Phạm vi triển khai**

- Camera and gallery permission
- Image crop and basic filters
- Caption validation
- Compress and upload progress
- Retry draft and cancel upload
- Post success navigation
**Tiêu chí nghiệm thu**

- Quyền bị từ chối có hướng dẫn
- Ảnh lớn được nén trong giới hạn
- Mạng gián đoạn không tạo post trùng
- Draft còn sau khi app bị đóng đột ngột
**Bằng chứng kiểm thử.** Widget test composer states; Integration test capture to feed; Kiểm thử Android and iOS permissions.

**Commit gợi ý.** feat(flutter-post): add capture composer and resilient upload

#### LC-S2-03 Flutter Feed Filter và Media Detail

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên A | 3 ngày | LC-S2-01 |

**Kết quả cần đạt.** Feed hiển thị bài mới nhất của bạn bè, có pull-to-refresh, phân trang và lọc theo một người bạn.

**Phạm vi triển khai**

- Home feed and skeleton loading
- Cursor pagination
- Filter by friend
- Media detail and save to device
- Empty and blocked content states
- Cache last successful page
**Tiêu chí nghiệm thu**

- Filter chỉ trả bài của friend đã chọn
- Refresh không nhân đôi item
- Lưu ảnh xử lý đúng quyền Photos
- Bài bị xóa hoặc block biến mất sau refresh
**Bằng chứng kiểm thử.** Golden or widget tests; Pagination integration test; Offline cache smoke test.

**Commit gợi ý.** feat(flutter-feed): add friend filter pagination and media detail

#### LC-S2-04 Post Service AI Generation Mock và entitlement hook

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P1 | Thành viên C | 3 ngày | LC-S2-01; Subscription hook có thể mock |

**Kết quả cần đạt.** Post Service tạo AI generation job giả lập, có trạng thái và điểm chặn entitlement để thay provider sau.

**Phạm vi triển khai**

- Create get retry generation
- PENDING SUCCESS FAILED state machine
- Prompt and input validation
- Fake worker with deterministic result
- Fallback to original media
- Call Subscription Service entitlement interface
**Tiêu chí nghiệm thu**

- Retry không tạo nhiều charge giả
- Timeout không chặn đăng ảnh gốc
- Chỉ tier được phép mới dùng AI
- Provider adapter có thể thay mà không đổi API public
**Bằng chứng kiểm thử.** State transition tests; Contract test entitlement; Failure and timeout test.

**Commit gợi ý.** feat(post-ai): add mock generation job and entitlement boundary

### Sprint 3 Chat và Notification

26/10/2026 đến 08/11/2026. Bạn bè nhắn tin realtime với trạng thái cơ bản; thông báo in-app và email hoạt động qua adapter thay thế được.

#### LC-S3-01 Chat Service Conversation Message và Realtime

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên C | 5 ngày | LC-S1-03 |

**Kết quả cần đạt.** Chat Service hỗ trợ hội thoại một-một, lịch sử tin nhắn và WebSocket với SENT DELIVERED READ.

**Phạm vi triển khai**

- Create or get one-to-one conversation
- Message history cursor pagination
- WebSocket authenticate and authorize
- Client message ID for idempotency
- Delivery and read receipt
- Friend and block check qua User Service
**Tiêu chí nghiệm thu**

- Non-friend hoặc blocked pair không chat
- Reconnect không tạo message trùng
- Ordering ổn định theo server timestamp
- Receipt chỉ tiến về phía trước
**Bằng chứng kiểm thử.** Unit test status transitions; Two-client WebSocket integration test; Reconnect and duplicate test.

**Commit gợi ý.** feat(chat): add one-to-one realtime messaging and receipts

#### LC-S3-02 Flutter Conversation Chat và Offline Queue

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên A | 5 ngày | LC-S3-01 |

**Kết quả cần đạt.** Ứng dụng hiển thị danh sách hội thoại, lịch sử và gửi tin bền vững khi kết nối chập chờn.

**Phạm vi triển khai**

- Conversation list
- Chat room and pagination
- WebSocket lifecycle
- Local pending queue
- Retry with client message ID
- Receipt and connection status UI
**Tiêu chí nghiệm thu**

- Tin pending hiện ngay
- Reconnect tự gửi lại một lần an toàn
- Không mất hoặc trùng tin
- Read receipt chỉ gửi khi màn hình thực sự xem
**Bằng chứng kiểm thử.** Widget tests message states; Integration test airplane mode and reconnect; Long history performance test.

**Commit gợi ý.** feat(flutter-chat): add realtime chat and offline queue

#### LC-S3-03 Notification Service In App Push Adapter và Email

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên C | 4 ngày | LC-S1-01 |

**Kết quả cần đạt.** Notification Service lưu notification, quản lý device token và gửi email hoặc push qua adapter mock trong MVP.

**Phạm vi triển khai**

- Notification inbox and unread count
- Mark read and read all
- Device token register rotate disable
- Email template for auth and subscription events
- Push provider interface with mock
- Internal endpoints with API key
**Tiêu chí nghiệm thu**

- Event trùng không tạo notification trùng
- Token logout bị disable
- Email không lộ token trong log
- Failed delivery có retry giới hạn
**Bằng chứng kiểm thử.** Idempotency test; Template rendering test; Internal API authentication test.

**Commit gợi ý.** feat(notification): add inbox device tokens and email adapter

#### LC-S3-04 Flutter Notification Center và Deep Link

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P1 | Thành viên A | 3 ngày | LC-S3-03 |

**Kết quả cần đạt.** Người dùng xem, đánh dấu và mở đúng màn hình từ thông báo friend, post, chat, subscription hoặc report.

**Phạm vi triển khai**

- Notification list and unread badge
- Mark read and read all
- Typed deep-link router
- Foreground notification banner
- Permission explanation
- Unknown or deleted target fallback
**Tiêu chí nghiệm thu**

- Mỗi notification type mở đúng route
- Deleted target hiển thị thông báo an toàn
- Badge đồng bộ sau read
- Không yêu cầu push permission trước khi giải thích giá trị
**Bằng chứng kiểm thử.** Route mapping unit test; Widget test unread state; Cold-start deep link smoke test.

**Commit gợi ý.** feat(flutter-notification): add inbox badge and deep links

### Sprint 4 Subscription và Admin

09/11/2026 đến 22/11/2026. Tài khoản có tier Free và Plus, payment sandbox cập nhật entitlement an toàn, report được xử lý qua Admin Service.

#### LC-S4-01 Subscription Service Tier và Entitlement

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên B | 4 ngày | LC-S1-01 |

**Kết quả cần đạt.** Subscription Service quản lý tier tài khoản và cung cấp entitlement ổn định cho Post AI và các giới hạn sử dụng.

**Phạm vi triển khai**

- FREE and PLUS catalog
- Account subscription state
- Upgrade downgrade cancel at period end
- Entitlement endpoint and cache
- Admin override with audit note
- Default FREE provisioning
**Tiêu chí nghiệm thu**

- Mọi user có đúng một active tier
- Tier change có effective time rõ ràng
- Service khác chỉ đọc entitlement qua contract
- Override được audit
**Bằng chứng kiểm thử.** State transition tests; Concurrency test update tier; Contract test Post Service.

**Commit gợi ý.** feat(subscription): add tiers lifecycle and entitlements

#### LC-S4-02 Payment Sandbox Checkout và Webhook

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên B | 4 ngày | LC-S4-01 |

**Kết quả cần đạt.** Người dùng nâng cấp Plus qua payment sandbox; webhook là nguồn sự thật và được xử lý idempotent.

**Phạm vi triển khai**

- Create checkout session
- Verify webhook signature
- Webhook event deduplication
- Map payment to subscription state
- Failed payment and grace handling
- Refund or cancel sandbox path
**Tiêu chí nghiệm thu**

- Không cập nhật tier chỉ từ client callback
- Webhook gửi lại không nhân đôi transaction
- Sai signature bị từ chối
- Payment failure không vô hiệu tài khoản
**Bằng chứng kiểm thử.** Webhook fixture tests; Replay and out-of-order events; End-to-end sandbox checkout.

**Commit gợi ý.** feat(payment): add sandbox checkout and idempotent webhook

#### LC-S4-03 Flutter Paywall và Quản lý Tier

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P1 | Thành viên A | 3 ngày | LC-S4-01, LC-S4-02 |

**Kết quả cần đạt.** Ứng dụng hiển thị tier hiện tại, quyền lợi, checkout và trạng thái gia hạn hoặc hủy rõ ràng.

**Phạm vi triển khai**

- Tier comparison
- Upgrade checkout launch
- Restore or refresh entitlement
- Manage cancel at period end
- Gate AI feature
- Payment pending and failed UI
**Tiêu chí nghiệm thu**

- UI không tự suy diễn entitlement
- Sau webhook app refresh đúng tier
- AI bị khóa hoặc mở đúng trạng thái
- Giá sandbox được ghi rõ
**Bằng chứng kiểm thử.** Widget test tier states; Integration test checkout return; Test delayed webhook.

**Commit gợi ý.** feat(flutter-subscription): add paywall and tier management

#### LC-S4-04 Admin Service Report và Moderation

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên C | 5 ngày | LC-S1-03, LC-S2-01, LC-S3-03 |

**Kết quả cần đạt.** User report post hoặc account; admin xem hàng chờ và áp dụng HIDE POST, WARN USER, BAN USER hoặc REJECT.

**Phạm vi triển khai**

- Create report with reason and target
- Admin role and protected endpoints
- Queue filter and detail
- Resolve or reject with note
- Internal calls to User and Post
- Audit record and result notification
**Tiêu chí nghiệm thu**

- Một user không spam report trùng
- Action chỉ áp dụng khi admin authorized
- Post hidden hoặc user banned phản ánh ngay
- Mọi quyết định có actor time note
**Bằng chứng kiểm thử.** Authorization tests; Workflow integration test; Failure compensation test when downstream unavailable.

**Commit gợi ý.** feat(admin): add report queue moderation and audit

### Sprint 5 Hardening và phát hành MVP

23/11/2026 đến 06/12/2026. Hoàn thiện luồng end-to-end, bảo mật, quan sát hệ thống và phát hành bản thử nghiệm cho Android cùng iOS.

#### LC-S5-01 Observability Security và Data Retention

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên C | 4 ngày | Tất cả service |

**Kết quả cần đạt.** Mọi request truy vết được; secret, rate limit, quyền nội bộ và chính sách xóa dữ liệu đạt baseline MVP.

**Phạm vi triển khai**

- Structured logs and correlation ID
- Service health and basic metrics
- Gateway and internal rate limits
- Secret rotation checklist
- Media and account deletion retention
- PII masking and log review
**Tiêu chí nghiệm thu**

- Có thể truy một request qua service
- Internal endpoint không public
- Log không chứa token OTP hoặc payment payload nhạy cảm
- Retention job có dry-run
**Bằng chứng kiểm thử.** Security smoke test; Rate-limit test; Log sampling review; Retention integration test.

**Commit gợi ý.** chore(security): harden observability secrets and retention

#### LC-S5-02 Automated Contract Integration và End to End Tests

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Cả nhóm | 5 ngày | LC-S1 đến LC-S4 |

**Kết quả cần đạt.** Luồng chính từ đăng nhập đến payment và report được kiểm thử tự động đủ để release có thể lặp lại.

**Phạm vi triển khai**

- API contract tests giữa services
- Database migration from empty
- Flutter integration test critical paths
- Two-user chat and post scenario
- Payment webhook and entitlement scenario
- Admin report scenario
**Tiêu chí nghiệm thu**

- Critical path pass trong CI
- Không còn P0 defect mở
- Flaky test được cô lập hoặc sửa
- Có test data reset script
**Bằng chứng kiểm thử.** Auth to friends to post; Chat and notification; Subscription and AI gate; Report and moderation.

**Commit gợi ý.** test(e2e): cover MVP critical journeys

#### LC-S5-03 Staging và Flutter Beta Release

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P0 | Thành viên A và C | 4 ngày | LC-S5-01, LC-S5-02 |

**Kết quả cần đạt.** Android beta và iOS TestFlight kết nối staging qua HTTPS, có rollback và release notes.

**Phạm vi triển khai**

- Deploy Gateway services database Redis and storage
- TLS and domain config
- Seed admin and test accounts
- Android internal testing build
- iOS TestFlight build
- Rollback and smoke checklist
**Tiêu chí nghiệm thu**

- Hai nền tảng cài được ngoài máy dev
- App không chứa backend secret
- Health and smoke pass sau deploy
- Rollback được thử ít nhất một lần
**Bằng chứng kiểm thử.** Fresh install; Upgrade from previous build; Staging smoke suite; Crash-free manual walkthrough.

**Commit gợi ý.** release: publish LocCoc MVP beta

#### LC-S5-04 Tài liệu vận hành Demo và Bàn giao

| Ưu tiên | Owner | Ước lượng | Phụ thuộc |
| --- | --- | --- | --- |
| P1 | Cả nhóm | 2 ngày | Toàn bộ MVP |

**Kết quả cần đạt.** Một thành viên mới có thể chạy local, hiểu dịch vụ, demo và xử lý sự cố phổ biến mà không cần hỏi tác giả.

**Phạm vi triển khai**

- README quick start
- Architecture and API links
- Runbook deploy rollback backup
- Known limitations and mock providers
- Demo script for two users and admin
- Backlog after MVP
**Tiêu chí nghiệm thu**

- Clean machine setup theo README thành công
- Demo hoàn thành trong 15 phút
- Mọi mock và credential thiếu được ghi rõ
- Trello và tài liệu Word cùng version
**Bằng chứng kiểm thử.** Peer runbook walkthrough; Broken-service recovery drill; Link audit.

**Commit gợi ý.** docs: add runbook demo and MVP handover

### Icebox sau MVP

#### LC-I-01 Tích hợp AI provider thật

Chỉ bắt đầu khi có ngân sách, quota, moderation và số đo chi phí.

#### LC-I-02 Tích hợp SMS OTP và push provider production

Cần credential, thiết bị thật và chính sách chống abuse.

#### LC-I-03 Widget GIF group chat và public discovery

Ngoài phạm vi MVP ba người; đánh giá lại sau beta.

### Definition of Ready

- Task có outcome, owner, estimate, dependency và acceptance criteria
- API hoặc UI contract đủ rõ để người thực hiện không phải đoán
- Không còn blocker về credential, dữ liệu hoặc quyền truy cập
- Task vừa trong tối đa năm ngày làm việc của một người
- Test approach và commit naming được ghi trên card
### Definition of Done

- Code đã merge qua pull request và liên kết Trello ID
- Build, test và migration check pass trong CI
- Không có P0 hoặc P1 defect mở cho phạm vi task
- API có validation, authorization và error response ổn định
- Flutter có loading, empty, error và retry state
- Tài liệu hoặc OpenAPI được cập nhật
- Demo được trên môi trường tích hợp và có bằng chứng test
### Rủi ro và biện pháp

| Rủi ro | Mức | Dấu hiệu | Biện pháp |
| --- | --- | --- | --- |
| GitHub chưa có commit | Cao | Không có branch hoặc tag | Khôi phục baseline trước mọi ước lượng Done |
| Nhóm bị phân tán bởi microservice | Cao | Nhiều service nhưng không có vertical slice | Monorepo, shared platform, REST và một DB cluster |
| Flutter và API lệch contract | Cao | UI chờ endpoint hoặc field thay đổi | OpenAPI contract review đầu sprint và contract test |
| Chat mất hoặc trùng tin | Trung bình | Reconnect tạo duplicate | Client message ID, idempotency và integration test |
| Payment cập nhật tier sai | Cao | Tin client callback | Webhook signature, dedupe và event log |
| AI hoặc push làm trễ release | Trung bình | Chờ credential hoặc quota | Giữ adapter mock; provider thật ở Icebox |
| Ba người review không đủ | Trung bình | Một người sở hữu module không ai hiểu | Pair walkthrough và mandatory cross-review |

### Quy ước Git và liên kết Trello

- Branch: feature/LC-S2-01-post-feed hoặc fix/LC-S3-01-message-dedup
- Commit: feat(post): LC-S2-01 add cursor feed
- Pull request phải gắn URL Trello card và ảnh hoặc log test
- Card chỉ chuyển Review and QA khi pull request đã mở
- Card chỉ chuyển Done khi merge, CI xanh và acceptance criteria hoàn tất
- Tag release theo semver, ví dụ v0.1.0-beta.1
### Nguồn tham chiếu

- **LocCoc tổng quan:** https://app.notion.com/p/3947b662e4cd80359960e81fc888317e
- **LocCoc Use case danh sách:** https://app.notion.com/p/d83d4bba0fbc49d3a3933802efdcb90f
- **LocCoc Use case chi tiết:** https://app.notion.com/p/a02b8343b8e1474081baafdb213066c2
- **LocCoc thiết kế cơ sở dữ liệu:** https://app.notion.com/p/7aa8cdfd0aed4d05a859d3037c5825cb
- **LocCoc kiến trúc và sprint plan:** https://app.notion.com/p/e1c15a30f6534d32b2bb13bb99af0514
- **LocCoc Backend Implementation Tracker:** https://app.notion.com/p/3a67b662e4cd8109a9f4d5dde79c827a
- **Trello Project Management:** https://trello.com/b/xdP6DSbD/project-management
- GitHub repository: https://github.com/NhanTr/social_media_project_manager
