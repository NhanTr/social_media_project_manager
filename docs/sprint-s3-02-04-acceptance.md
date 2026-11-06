# S3-02 / S3-04 — Flutter trước backend

Ngày: 06/11/2026. Branch `feature/LC-S3-02-LC-S3-04-flutter`, dựa trên nhánh contract tại `9243b85` (dev `eefc3a5`). Người dùng yêu cầu làm Flutter trước, backend hoàn thiện sẽ nối sau. Không sửa backend runtime và không chuyển Trello Done.

## Sử dụng

Trong `loccoc_application`:

```sh
flutter pub get
flutter run -t lib/s3_demo.dart --dart-define=APP_ENV=dev
```

Entry riêng này mở chat + inbox mà không cần auth/backend; không có auth bypass trong ứng dụng chính. Chỉ chạy với APP_ENV=dev. Ứng dụng chính (`lib/main.dart`) sau đăng nhập ở môi trường dev cũng tích hợp S3, có header “Dữ liệu mô phỏng”, nút thông báo/badge và chat thay placeholder. `--dart-define=S3_DEMO=false` tắt demo trong ứng dụng chính. Staging/prod không tự tạo repository mô phỏng.

Thử chat: mở Minh, bấm biểu tượng mất mạng mô phỏng, gửi một tin, kiểm tra “Đang chờ gửi”, đóng/mở app rồi kết nối lại. Cùng clientMessageId được giữ trong queue. Mock server lưu bền vững để replay sau restart không tạo canonical message thứ hai. Gửi online chỉ thể hiện SENT; không giả DELIVERED/READ cho đối phương nếu chưa có event ACK tương ứng.

Thử notification: mở inbox, đọc một mục/đọc tất cả, xem badge; bấm “Tạo thông báo demo” để có foreground banner, chọn Mở để vào room10/focus message99. Các target friend/profile/post/subscription/report dùng màn hình xem trước riêng có nhãn mô phỏng. Unknown/invalid/deleted target dùng thông báo an toàn. Cài đặt thông báo giải thích trước khi gọi permission adapter; demo không cấp quyền OS hoặc đăng ký token thật.

Cold-start adapter smoke có thể truyền payload mẫu lúc khởi chạy:

```sh
flutter run -t lib/s3_demo.dart --dart-define=APP_ENV=dev --dart-define='S3_DEMO_INITIAL_PUSH={"notificationId":"997","type":"CHAT_MESSAGE","targetKind":"CONVERSATION","targetId":"10","messageId":"99"}'
```

Đây là payload injection vào app, không phải chứng minh OS/FCM cold-start delivery. `NotificationLaunchController` giữ intent trước login, consume một lần sau session sẵn sàng, dedup notificationId theo account và bỏ intent bound account khác. Foreground banner không tự mark-read; tap mới đánh dấu và điều hướng.

## Ranh giới để nối backend sau

- `features/chat/chat_repository.dart`: lifecycle connect/disconnect, conversation/history/send, delivered/read, event stream. ApiChatRepository sau này triển khai REST + STOMP theo [contract](../backend/docs/contracts/s3-mobile-api-contract.md). Controller không gọi endpoint hoặc biết HTTP. ChatMessage.fromJson/toJson hiện là định dạng local store (status lower-case); adapter API phải map DTO status upper-case và nullable legacy clientMessageId sang domain model, không dùng raw serializer local cho HTTP. Tên/avatar hội thoại phải lấy từ public summary được cấp quyền khi DTO conversation chỉ có user IDs.
- `features/notifications/notification_repository.dart`: inbox/count/read/read-all/event lifecycle. ApiNotificationRepository sau này dùng REST + push adapter.
- `features/notifications/notification_permission.dart`: OS permission boundary; inject adapter thật khi đã có push config.
- `features/s3/s3_session.dart`: composition root. Inject `AppShell.s3SessionFactory` để tạo S3Session với repository thật và `isDemo:false`.
- `NotificationLaunchController.offer` là đầu vào cho tap/cold-start của platform adapter. Adapter không quyết định account từ userId trong payload; quyền target phải kiểm tra ở public domain API. Khi nhận push thật, dùng recipientAccountId từ session đáng tin hoặc resolve ownership/target bằng API đã xác thực trước khi điều hướng intent chưa bound account; raw initialPayload hiện chỉ phục vụ injection demo.
- Demo persistence dùng namespace `s3-chat-demo` / `s3-notifications-demo`, tách khỏi posts. Adapter thật phải dùng namespace riêng như `s3-chat-api` / `s3-notifications-api`; **không đưa queue/notification demo lên backend**.
- Queue scope theo account, persist trước gửi; terminal error phải bấm retry, transient retry có backoff, reconcile ACK theo server id và stable key. Repository thật phải trả canonical ACK kể cả replay qua REST khi STOMP ACK đã mất.
- Subscription/report/profile preview chưa phải màn hình dữ liệu thật. Sau này nối public owner-only detail/resolver của từng service; post thật có thể dùng MediaDetailScreen hiện có; không dùng admin/internal API từ Flutter.

## Bằng chứng kiểm tra

Mốc trước triển khai: **102 Flutter tests pass**.

- Scoped domain handoff: chat 20 tests và notification 32 tests qua trước tích hợp cuối.
- Regression review đã chứng minh RED rồi sửa GREEN: ACK muộn giữ bản nháp mới; read-all fence trang lịch sử/refresh cũ, giữ đúng arrival mới và cho tải lại cursor.
- Browser smoke trên build standalone ở viewport khoảng 319×853: mở chat, gửi offline thấy pending, reconnect thấy một canonical SENT; mở inbox/badge, tạo foreground banner, Mở vào đúng conversation10/focus99. Banner đặt trên Overlay nên không bị route phòng chat che.
- Review độc lập tìm hai P2 nêu trên; đã sửa trong cùng nhánh. Review không tìm thấy lỗi nghiêm trọng về queue mất/trùng tin. Xác minh cuối trên tree đã sửa:

| Kiểm tra | Kết quả |
| --- | --- |
| `flutter test --timeout 30s` | PASS **168 tests**, gồm toàn bộ 102 test cũ và 66 test mới |
| `flutter analyze` | PASS, No issues found |
| `flutter build web` | PASS, `build/web`, gồm Wasm dry run |
| `flutter build web -t lib/s3_demo.dart --output build/s3-demo` | PASS trên tree đã sửa sau review, `build/s3-demo`, gồm Wasm dry run |
| `git diff --check` | PASS |

Các test mới bao gồm: durable queue/server restart, mất ACK, reconnect đồng thời, storage write/init failure, pagination/cursor failure, receipt monotonic + visible/covered route, 2.000-message lazy rendering, typed parser sáu nhóm/unknown/malformed, stale count/read-all/page races, badge/read UI, permission lifecycle, foreground overlay trên pushed route và cold-start consume-once/account binding.

## Phạm vi nghiệm thu còn cần backend/thiết bị

Frontend dùng fake transport có lưu bền vững, không có production HTTP/STOMP/FCM/APNs adapter. Airplane-mode với transport thật, hai-client qua Gateway, token refresh/rotation/logout disable, PostgreSQL dedup đồng thời, OS cold-start push, Android/iOS real-device performance và provider config phải nghiệm thu khi backend đã tích hợp. Không coi demo/repository tests là bằng chứng những bước đó đã chạy.
