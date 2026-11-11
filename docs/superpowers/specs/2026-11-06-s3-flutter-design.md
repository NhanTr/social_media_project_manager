# S3-02 / S3-04 Flutter trước khi backend sẵn sàng

User yêu cầu triển khai hai task Flutter trước, backend hoàn thiện sẽ nối sau. Source of truth: các card S3-02/S3-04 và `backend/docs/contracts/s3-mobile-api-contract.md`; không cần thay backend.

## Thiết kế triển khai

Giữ Flutter/ChangeNotifier hiện tại. Mỗi domain có model, repository interface, demo repository, controller và màn hình riêng. Shell chỉ kết nối các controller và route. Demo là chế độ dev có nhãn rõ ràng; staging/prod không tự trả dữ liệu giả. Backend sau này thay repository/transport theo API contract, không thay UI/queue/router.

Chat: list hội thoại, room dùng ListView.builder và cursor pagination, trạng thái kết nối, pending xuất hiện sau khi persist queue, retry giữ clientMessageId, reconcile theo canonical ID/key. Queue và demo server snapshot lưu bền vững theo account để mất ACK/restart vẫn dedup. Controller chịu lifecycle connect/disconnect/reconnect; route/app visibility và visible message quyết định read watermark. Không gửi READ chỉ vì IndexedStack đang mounted. Chặn retry chồng, xử lý storage/network/terminal errors, account switch và dispose.

Notification: paginated inbox, unread badge, mark read/read-all + authoritative reload count, typed target parser không đoán conversation từ messageId. Foreground banner và cold-start payload cùng router; unknown/deleted/forbidden dùng fallback. Permission explanation trước khi gọi permission adapter; demo adapter không xin OS permission hoặc giả vờ push thật. Các target backend/screen chưa có dùng màn hình kết quả demo hoặc fallback rõ ràng; Post dùng media detail hiện hữu khi thuộc nguồn API thật.

Demo có điều khiển mất mạng/kết nối lại và event notification để test thủ công. Không thêm package nếu SDK/dependency sẵn có đủ dùng. Không implement production REST/STOMP/push adapter trước backend; document điểm thay thế và giới hạn nghiệm thu thiết bị.

## Bằng chứng cần có

Unit/controller tests: queue restart + stable key, ACK loss/concurrent flush, cursor merge và receipt monotonic, hidden/background route, account disposal; notification typed targets/malformed payload/read race/stale result; widget tests cho trạng thái message, inbox/badge/permission/fallback; shell cold-start/deep-link integration và long history smoke. Chạy toàn bộ flutter test và flutter analyze, build web để kiểm tra compile; không claim airplane/OS push thật từ fake transport.
