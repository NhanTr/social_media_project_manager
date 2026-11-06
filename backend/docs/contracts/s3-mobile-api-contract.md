# LC-S3-02 / LC-S3-04 — rà soát và API contract bàn giao backend

Ngày kiểm tra: **06/11/2026 (Asia/Ho_Chi_Minh)**. Phạm vi đã xác nhận: rà soát và viết contract; không triển khai Flutter/backend trong thay đổi này.

## 1. Nguồn và kết luận

Đã fetch `origin` trước khi kiểm tra, đối chiếu controller, DTO, service, repository, WebSocket config, migration và Flutter shell. Mốc source:

| Nguồn | Commit | Kết quả |
| --- | --- | --- |
| `origin/dev` | `eefc3a5` | Nền tích hợp; S2-02/S2-03 đã merge; chat và notification còn bản cũ |
| `origin/feature/LC-S3-01-Chat-Service` | `b8e6cb4` | Có clientMessageId, delivered/read receipt và tests mới; chưa nằm trong dev |
| `origin/feature/LC-S3-03-—-Notification-Service-In-app-Push-Adapter-và-Email` | `0d17536` | Có unread count, token rotation, email/push mock; bao gồm commit chat b8e6cb4; chưa nằm trong dev |

Nguồn công việc: [S3-02](https://trello.com/c/k11jEBR3), [S3-04](https://trello.com/c/rrUmEYio), phụ thuộc [S3-01](https://trello.com/c/6J78dW7o), [S3-03](https://trello.com/c/QQUTMPxr). Cả bốn card ở “03 Đang làm” tại thời điểm đọc; trạng thái board không phải bằng chứng đã nghiệm thu.

**S3-02/S3-04 chưa thể nghiệm thu từ source đã tích hợp.** Flutter trên dev vẫn có chat placeholder (`loccoc_application/lib/features/shell/app_shell.dart`), chưa có conversation room, offline queue, notification center hay typed notification router. Backend có phần lõi, nhưng còn các khoảng trống dưới đây. Đây là kết luận static review, không phải xác nhận runtime/CI của hai nhánh backend.

| Khả năng | dev | Nhánh backend riêng | Việc còn lại |
| --- | --- | --- | --- |
| List/open conversation, history, gửi REST/STOMP | Có | Có | Flutter chưa triển khai; list hiện là array không phân trang, thiếu profile/preview/unread |
| clientMessageId | Không | Có ở b8e6cb4, optional | Retry đồng thời phải trả cùng kết quả; phát hiện payload khác cùng key |
| DELIVERED / READ | Read toàn conversation | Thêm delivered và broadcast receipt | Watermark tin thực nhận/đã xem; không suy DELIVERED chỉ từ online |
| Cursor history | Có | Có | Cursor encode epoch millis nhưng query so sánh Instant gốc: cần test/fix precision trước nghiệm thu |
| Notification inbox/read/read-all | Có | Có | readAt phải ổn định khi retry; client refetch unread sau ghi |
| Unread count | Không | Có ở 0d17536 | Chờ backend review/merge và test qua Gateway |
| Deep link typed | Không | Chỉ type/referenceId | Thêm target rõ nghĩa; CHAT_MESSAGE referenceId hiện là messageId, không phải conversationId |
| Subscription notification | Không | Không | Chưa có enum/event/target; dịch vụ subscription thuộc Sprint 4 |
| Push/email | Mock cơ bản | Mock có adapter/retry | Provider thật hoãn hậu MVP; không thể xác nhận cold-start push trên thiết bị từ mock |
| Device rotation/logout | Register/delete | Thêm oldToken và internal disable-user | Kiểm tra ownership oldToken; xác nhận identity logout thực sự gọi disable hoặc client disable trước logout |

Các source quan trọng nằm dưới `backend/chat-service/src/main/java/com/nhantr/chat_service/` và `backend/notification-service/src/main/java/com/nhantr/notification_service/` tại các commit nêu trên. Không cherry-pick/merge hai nhánh này để tránh thay đổi công việc owner C.

## 2. Cách dùng contract

[OpenAPI JSON](s3-mobile.openapi.json) là **draft contract đích** để backend review và làm sau, không phải OpenAPI sinh từ runtime. Mỗi operation có `x-implementation-status`; phần đề xuất được mô tả trong summary/schema. Không dùng file này để kết luận dev đã hỗ trợ các field/behavior mới.

Giữ URL và response envelope hiện có; bổ sung tối thiểu để Flutter có thể hoàn thành yêu cầu đã ghi trong card. Các quyết định kỹ thuật về watermark, atomic replay và typed target dưới đây là đề xuất của lần rà soát này, chưa phải xác nhận đồng thuận của backend owner. Không thay API list conversation sang PageResponse trong contract này vì sẽ làm hỏng caller cũ; pagination/profile/preview của conversation là cải tiến riêng nếu cần.

Quy ước chung:

- REST qua Gateway `/api/v1/**`, `Authorization: Bearer <access-token>`, JWT đã chấp nhận terms. Không gửi token trong URL.
- `X-Request-Id` đi xuyên request/response; lỗi domain dùng ProblemDetail với `code`, `detail`, `status`, `requestId`. Cần kiểm tra thêm response từ security filter qua Gateway.
- ID JSON là positive int64; thời gian RFC 3339 UTC; field nullable giữ null, client không tự đổi thành 0/chuỗi rỗng.
- Page: `{ "items": [], "nextCursor": null, "hasNext": false }`. Cursor opaque, không decode hoặc tự tạo.
- REST lỗi 401: refresh một lần theo session flow hiện có; refresh thất bại dừng gửi và yêu cầu đăng nhập. 403/404/409 terminal không tự retry vô hạn. Timeout/mất mạng/5xx chỉ retry có backoff và giữ nguyên idempotency key.
- Internal API không expose qua Gateway, chỉ service gọi bằng `X-Internal-Api-Key`.

## 3. S3-02 — REST và dữ liệu

| Method/path | Input | Response | Trạng thái |
| --- | --- | --- | --- |
| GET `/api/v1/conversations` | Không | `Conversation[]` | Có trên dev; không phải PageResponse |
| POST `/api/v1/conversations/{friendId}` | Không body | 201 `Conversation` kể cả mở lại | Có; chỉ bạn bè không block |
| GET `/api/v1/conversations/{id}/messages` | `cursor?`, `limit=50`, tối đa 100 ở b8e6cb4 | `PageResponse<Message>` | Có; thứ tự mới → cũ theo `(sentAt DESC,id DESC)` |
| POST `/api/v1/messages` | SendMessage | 201 Message | Có; clientMessageId mới ở nhánh chat |
| POST `/api/v1/conversations/{id}/delivered` | Receipt watermark | `{updated}` | Endpoint ở nhánh chat; body watermark chưa có |
| POST `/api/v1/conversations/{id}/read` | Receipt watermark | `{updated}` | Endpoint có; body watermark chưa có |

Conversation hiện có: `{id,userId1,userId2,lastMessageAt,createdAt}`; lastMessageAt có thể null. Chưa có tên/avatar, lastMessage hoặc unreadCount trong DTO. Flutter chỉ enrich từ public API được phép, không gọi identity internal batch.

SendMessage đích (dùng chung REST/STOMP):

```json
{
  "conversationId": 10,
  "messageType": "TEXT",
  "clientMessageId": "6ae12d8e-aad9-46cd-8a79-8c39d78146a8",
  "content": "Xin chào",
  "mediaUrl": null,
  "thumbnailUrl": null
}
```

Message response đích:

```json
{
  "id": 99,
  "conversationId": 10,
  "senderId": 1,
  "clientMessageId": "6ae12d8e-aad9-46cd-8a79-8c39d78146a8",
  "content": "Xin chào",
  "mediaUrl": null,
  "thumbnailUrl": null,
  "messageType": "TEXT",
  "status": "SENT",
  "sentAt": "2026-11-06T07:40:00.123456Z",
  "deliveredAt": null,
  "readAt": null
}
```

Validation: client mới bắt buộc gửi clientMessageId không blank, tối đa 100 ký tự, nên dùng UUID sinh đúng một lần trước network. TEXT/SYSTEM cần content không blank, tối đa 5000 ký tự; IMAGE/GIF cần mediaUrl, URL tối đa 2048. Enum hiện có TEXT/IMAGE/GIF/SYSTEM; quyền tạo SYSTEM từ client cần backend review trước khi Flutter expose.

### Retry/offline và xác nhận lưu thành công — đề xuất bắt buộc cho nghiệm thu

1. Flutter persist queue theo account + conversation + clientMessageId trước gửi; PENDING/FAILED là trạng thái local, không thêm vào enum server SENT/DELIVERED/READ.
2. Backend unique key `(conversationId,senderId,clientMessageId)` đã có ở V8 của nhánh chat. Cần serialize/handle unique race để hai request đồng thời cùng key/payload đều trả cùng Message, không 500. So sánh payload đã normalize; khác payload trả 409 `MESSAGE_REQUEST_CONFLICT`. Không gửi lại broadcast/notification khi replay.
3. b8e6cb4 mới lookup rồi insert và trả record cũ bất kể payload: unique index ngăn lưu hai hàng nhưng chưa đảm bảo concurrent caller nhận canonical response. Cần test PostgreSQL thật cho race; unit repository giả chưa đủ.
4. REST 201 hoặc Message event có đúng sender/conversation/clientMessageId và server id mới xác nhận bền vững. STOMP RECEIPT chỉ xác nhận frame transport, không tự coi là business ACK.
5. b8e6cb4 không broadcast lại khi replay; WebSocket controller trả void. Nếu ACK bị mất, client dùng POST REST cùng key/payload để lấy canonical Message; không chờ topic vô hạn. Không sinh key mới vì timeout.
6. Subscribe trước rồi tải history; merge theo server id, đồng thời reconcile pending theo key. Khi reconnect, tải lại từ đầu đến khi nối được với tập tin đã cache, không chỉ một trang 50 tin; deduplicate các trang/event để khôi phục khoảng offline dài. Tránh tiến cursor nếu request trang thất bại.
7. Logout/account switch dừng socket, hủy in-flight, không phát queue của account cũ sang phiên mới; storage scope account và policy giữ/xóa cần Flutter xử lý sau này.

### Receipt watermark — phần backend cần bổ sung

Body mới: `{ "lastMessageId": 99 }`. Bodyless request legacy vẫn được chấp nhận để tương thích, nhưng Flutter mới luôn gửi watermark. Chỉ bật read receipt mới sau khi xác minh backend đã triển khai watermark bằng version/config triển khai và integration test: server cũ có thể bỏ qua body lạ rồi đánh dấu toàn conversation, không được coi HTTP 200 là bằng chứng đã hỗ trợ watermark.

- Server resolve message trong đúng conversation và là tin từ đối phương; ID sai/khác conversation trả 400 `RECEIPT_TARGET_INVALID`, không cập nhật bất kỳ hàng nào.
- DELIVERED chỉ khi client đã nhận/lưu message. Online presence không chứng minh delivery; nhánh chat hiện gán DELIVERED ngay khi receiver online, cần sửa để trạng thái phản ánh ACK thật.
- READ chỉ khi app foreground, route chat đang active và tin tương ứng đã hiện trong vùng xem. IndexedStack giữ tab mounted không có nghĩa user đang xem.
- Update chỉ tin đến có thứ tự `(sentAt,id) <= watermark`; không đánh dấu tin mới đến sau watermark hoặc tin chưa xem. Không dùng `id <= lastMessageId` thay cho server ordering.
- Trạng thái tiến `SENT → DELIVERED → READ`; READ có thể đi từ SENT và phải điền deliveredAt nếu thiếu. Retry cùng watermark là no-op, trả updated=0; timestamp lần đầu không bị đổi.
- Event đích thêm lastMessageId; hiện tại event chỉ có conversationId, participantId, receiptType, timestamp, updatedCount. Client cũ nhận unknown field được; client mới thiếu watermark thì refetch history để xác nhận trạng thái, không đánh dấu cả room theo receipt chung.

### STOMP contract

Native WebSocket `/ws/chat` (không dùng SockJS): CONNECT native header `Authorization: Bearer <access-token>`; SUBSCRIBE `/topic/conversations/{conversationId}` sau CONNECTED. Gửi JSON đến `/app/chat.send`, `/app/chat.delivered`, `/app/chat.read`. Hai receipt destination chỉ có ở nhánh b8e6cb4; payload hiện tại là `{conversationId}`, đích bổ sung lastMessageId.

Topic hiện trộn MessageResponse và ChatReceiptEvent, **chưa có envelope `{type,payload}`**. Dispatch bằng `receiptType` cho receipt hoặc `id` + `messageType` cho message; bỏ qua shape không biết và ghi diagnostics không có token/nội dung riêng tư.

Receipt đích (lastMessageId là field mới):

```json
{
  "conversationId": 10,
  "participantId": 2,
  "receiptType": "READ",
  "timestamp": "2026-11-06T07:41:00Z",
  "updatedCount": 1,
  "lastMessageId": 99
}
```

CONNECT phải bind principal thực, SUBSCRIBE/SEND kiểm tra membership; open/send kiểm tra friendship/block. Reject topic không thuộc allowed conversation namespace. Refresh token không hợp lệ cho CONNECT. Reconnect phải lấy access token hiện tại, subscribe lại và reconcile qua REST; logout/background dispose socket theo lifecycle. Heartbeat/reconnect backoff là yêu cầu client/server cần kiểm chứng, không có durable topic replay hiện tại.

Gateway hiện khai báo route HTTP Web MVC cho `/ws/chat/**`; cấu hình đó **chưa phải bằng chứng proxy Upgrade WebSocket hoạt động**. Nghiệm thu hai client phải đi qua Gateway cổng 8080; gọi trực tiếp chat:8085 không đủ.

## 4. S3-04 — notification, badge và deep link

| Method/path | Input | Response | Trạng thái |
| --- | --- | --- | --- |
| GET `/api/v1/notifications` | cursor?, limit=20, tối đa 50 | PageResponse Notification | Có trên dev |
| GET `/api/v1/notifications/unread-count` | Không | `{count:3}` | Chỉ ở 0d17536 |
| POST `/api/v1/notifications/{id}/read` | Không body | Notification | Owner only, 404 nếu không thuộc user |
| POST `/api/v1/notifications/read-all` | Không body | `{updated:3}` | Không dùng updated thay unread count |
| POST `/api/v1/device-tokens` | `{token,platform,oldToken?}` | 201 `{id,token,platform,active}` | oldToken mới ở 0d17536 |
| DELETE `/api/v1/device-tokens/{token}` | URL-encoded token | 204 | Chỉ token của user; gọi trước logout |

Notification hiện có: `{id,userId,actorUserId,type,referenceId,title,body,read,createdAt,readAt}`. Không đổi tên read thành isRead trong JSON. Sau mark-read/read-all thành công, merge response rồi refetch unread count; không set badge=0 vì có thể có notification mới trong lúc request chạy. Retry mark-read giữ nguyên readAt đầu tiên (nhánh hiện tại gán Instant.now mỗi lần, cần sửa). Nếu mark-read lỗi vẫn cho điều hướng, badge reload sau; không đánh dấu thành công giả.

### Typed target — field mới đề xuất, giữ referenceId để tương thích

```json
{
  "id": 501,
  "userId": 2,
  "actorUserId": 1,
  "type": "CHAT_MESSAGE",
  "referenceId": 99,
  "title": "Tin nhắn mới",
  "body": "Xin chào",
  "read": false,
  "createdAt": "2026-11-06T07:40:00Z",
  "readAt": null,
  "target": { "kind": "CONVERSATION", "id": 10, "messageId": 99 }
}
```

| Notification type | target.kind / id | Flutter route đích | Dữ liệu/capability còn thiếu |
| --- | --- | --- | --- |
| FRIEND_REQUEST | FRIEND_REQUEST / requestId | Incoming friend requests, focus request | Enrich target từ event social; kiểm tra request còn pending |
| FRIEND_ACCEPT | PROFILE / actorUserId | Profile/bạn bè, focus user | Public profile detail chưa có; fallback danh sách bạn bè đến khi có route |
| NEW_POST | POST / postId | Media detail hiện có | GET post phải kiểm tra visibility/block/deleted |
| CHAT_MESSAGE | CONVERSATION / conversationId; messageId optional | Chat room, focus message | Chat caller hiện chỉ gửi messageId làm referenceId; phải bổ sung conversationId |
| SUBSCRIPTION_UPDATED (mới) | SUBSCRIPTION / subscriptionId | Subscription của user hiện tại | Enum/migration/internal event và owner-only subscription API/Flutter thuộc S4; chưa có |
| REPORT_RESULT | REPORT / reportId | Kết quả report của user | Hiện chỉ có admin detail; cần GET public owner-only `/api/v1/reports/{id}` hoặc payload kết quả an toàn; mobile không gọi admin API |

Field target là additive, không suy conversationId từ referenceId của chat. Record cũ thiếu target hoặc unknown type dùng fallback inbox/thông báo “Nội dung không còn khả dụng”; không đoán ID/route. Subscription/report/profile chưa có màn hình/API thì hiển thị fallback rõ ràng, không tuyên bố route đã đạt nghiệm thu.

Server xây target từ dữ liệu domain đáng tin, không nhận URL tùy ý từ push để Navigator mở trực tiếp. Tất cả target đều phải được kiểm tra quyền khi tải tài nguyên; 403/404/410 → thông báo an toàn và quay về inbox, không phân biệt target thuộc user khác hay đã xóa. Nếu app chưa có phiên thì giữ pending navigation, chỉ resolve sau login và không dùng dữ liệu account trước.

### Internal event và push adapter — đề xuất bổ sung

Giữ các path `/internal/v1/notifications/friend-request`, `friend-accept`, `new-post`, `chat-message`, `report-result`; truyền thêm target trong event và lưu cùng notification. Caller dùng Idempotency-Key ổn định cho event + recipient; replay không tạo inbox/push trùng. Retry đồng thời phải có kết quả canonical và kiểm tra payload conflict như nguyên tắc chat; lookup trước insert chưa đủ bảo đảm điều đó.

Chat event mẫu (body mới thêm target):

```json
{
  "userId": 2,
  "actorUserId": 1,
  "referenceId": 99,
  "title": null,
  "body": "Xin chào",
  "target": { "kind": "CONVERSATION", "id": 10, "messageId": 99 }
}
```

Đề xuất push data tối thiểu (FCM data values là string):

```json
{
  "notificationId": "501",
  "type": "CHAT_MESSAGE",
  "targetKind": "CONVERSATION",
  "targetId": "10",
  "messageId": "99"
}
```

Đây chưa phải payload mock hiện tại. Foreground banner, push tap và cold-start đều chuyển về cùng typed parser; dedup notificationId, refetch inbox/count và xác nhận target bằng public domain API. Không tin userId/URL trong push và không tự mark-read vì chỉ nhận push. Khi app đang active nhưng mock không phát push, Flutter có thể refresh/poll inbox; không có notification WebSocket/SSE trong code hiện tại.

Device lifecycle: giải thích giá trị trước OS permission; từ chối permission vẫn dùng inbox. Backend rotation phải kiểm tra oldToken thuộc current user trước deactivation (0d17536 đang findByToken không scope owner). Disable token trước logout; 404 token đã mất có thể coi cleanup xong. Internal disable-user hiện có ở nhánh notification nhưng cần wiring từ identity nếu muốn đảm bảo revoke khi logout từ xa. Redact token trong URL/access log; không log email auth token/OTP.

## 5. Error matrix đích

| HTTP/code | Hiện trạng | Hành vi client |
| --- | --- | --- |
| 400 VALIDATION_FAILED / INVALID_REQUEST / CURSOR_INVALID | Có theo DTO/domain; verify security/runtime | Hiện lỗi hợp lệ, không retry payload sai; cursor lỗi reload từ đầu |
| 400 MESSAGE_CONTENT_REQUIRED / MESSAGE_MEDIA_REQUIRED / INVALID_USER_PAIR | Có ở chat | Sửa nội dung/đối tượng |
| 400 RECEIPT_TARGET_INVALID | Đề xuất | Bỏ receipt sai; reload room |
| 401 (JWT invalid/expired) | Verify qua Gateway và CONNECT | Refresh một lần, thất bại thì dừng |
| 403 CHAT_NOT_ALLOWED / CONVERSATION_FORBIDDEN | Có ở chat | Dừng queue cho room, thông báo không thể gửi |
| 403 TERMS_ACCEPTANCE_REQUIRED | Có ở Gateway REST | Quay về terms flow |
| 404 CONVERSATION_NOT_FOUND / NOTIFICATION_NOT_FOUND / DEVICE_TOKEN_NOT_FOUND | Có | Fallback an toàn; token delete 404 xử lý idempotent cleanup |
| 409 MESSAGE_REQUEST_CONFLICT | Đề xuất | Đánh dấu FAILED, không đổi key và retry tự động |
| 5xx/timeout/disconnect | Có thể gặp | Retry có backoff, cùng key; không bỏ pending chỉ vì socket đã gửi frame |

## 6. Checklist bàn giao và kiểm thử cần bổ sung

Backend C ưu tiên trước: (1) review/merge nhánh chat và notification theo thứ tự dependency; (2) atomic replay/payload conflict, cursor precision và true delivery ACK; (3) receipt watermark + event; (4) persist typed target và enrich chat caller; (5) mark-read stable timestamp, rotation ownership và logout wiring. Subscription/report/profile capability giao service owner tương ứng, không thêm business logic vào Gateway/common-lib.

Không sửa/đổi tên migration đã áp dụng. Nhánh riêng hiện dùng V7 notification deliveries và V8 client message id; chỉ chọn số migration mới sau khi xác minh lịch sử merge thực tế. Target persistence/enum mới cần migration mới với grants/ownership, không sửa V1.

| Nhóm | Bằng chứng cần có trước Done |
| --- | --- |
| Chat idempotency | Hai REST/STOMP send đồng thời cùng key/payload trả cùng id, một row và một side effect; khác payload 409; mất ACK rồi REST replay trả canonical message |
| History | Nhiều tin cùng timestamp và timestamp có microseconds, hơn 100 tin, đọc hết page không mất/trùng; reconnect sau khoảng offline vượt một page |
| Receipt | DELIVERED sau ACK; READ chỉ tin đến qua watermark; tin mới ngoài watermark không đổi; duplicate/out-of-order không giảm trạng thái; user khác không receipt/subscribe được |
| Gateway/STOMP | Hai client thật CONNECT/SUBSCRIBE/SEND qua :8080; expired/refresh JWT; non-member; block/unfriend; reconnect/background/logout |
| Queue/UI S3-02 | Widget PENDING/SENT/DELIVERED/READ/FAILED; kill/restart giữ queue; airplane/reconnect; account switch; long-history performance |
| Notification | Cursor/unread/read/read-all với arrival race; duplicate event; retry read giữ readAt; owner-only access; rotation không vô hiệu token người khác |
| Router/UI S3-04 | Mỗi type + unknown/missing target; CHAT messageId khác conversationId; target deleted/forbidden; badge sync; foreground banner; permission explanation; login/account switch |
| Cold start | Payload mẫu qua typed parser có thể test bằng fake adapter; smoke test OS push cold-start cần provider/config/thiết bị thật, mock backend chưa chứng minh được |

Kết quả kiểm tra file và môi trường được ghi riêng tại [báo cáo rà soát](../../../docs/sprint-s3-02-04-review-2026-11-06.md). Các test trong bảng là việc cần làm khi triển khai, không phải test đã chạy trong nhánh tài liệu này.
