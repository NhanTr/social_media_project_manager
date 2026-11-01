# Sprint S3-03 Implementation & Acceptance Review
## Notification Service In-app Push Adapter và Email (LC-S3-03)

- **Date:** 01 November 2026 (Asia/Ho_Chi_Minh)
- **Sprint:** S3
- **Priority:** P0
- **Owner:** Phùng Tuấn Dũng (Owner C)
- **Branch:** `feature/LC-S3-03-—-Notification-Service-In-app-Push-Adapter-và-Email`
- **Trello Card:** [LC-S3-03](https://trello.com/c/LC-S3-03)
- **Dependencies:** LC-S1-01

---

## 1. Kết quả đạt được (Scope of Delivery)

Theo nghiệp vụ ứng dụng **Locket Widget**:
1. **Notification Inbox & Unread Count:**
   - Hoàn thiện truy vấn `countByUserIdAndReadFalse` và endpoint `GET /api/v1/notifications/unread-count`.
   - Lưu trữ inbox phân trang cursor: `NEW_POST`, `FRIEND_REQUEST`, `FRIEND_ACCEPT`, `CHAT_MESSAGE`, `REPORT_RESULT`.
2. **Mark Read & Read All:**
   - Chạm xem chi tiết ảnh/thông báo: `POST /api/v1/notifications/{id}/read`.
   - Đọc toàn bộ để reset badge về 0: `POST /api/v1/notifications/read-all`.
3. **Device Token Lifecycle (Register / Rotate / Disable):**
   - Đăng ký token APNs (iOS) và FCM (Android).
   - Xoay vòng token (`oldToken`) khi OS cấp token mới, tự động hủy token cũ.
   - Khi Logout, vô hiệu hóa toàn bộ token qua `POST /internal/v1/device-tokens/disable-user/{userId}`, đảm bảo không đẩy ảnh bạn bè về máy cũ.
4. **Push Provider Interface Mock (Locket Widget Sync):**
   - Hỗ trợ cả 2 dạng: Visible Banner Alert và Background Widget Sync mang payload ảnh.
   - Mô phỏng và kiểm soát lỗi mạng tạm thời phục vụ kiểm thử retry.
5. **Email Template Engine (Auth & Locket Gold Subscription):**
   - Render HTML & Plaintext cho: `AUTH_WELCOME`, `AUTH_OTP_ALERT`, `SUBSCRIPTION_ACTIVATED` (Locket Gold), `SUBSCRIPTION_CANCELLED`.
   - Tự động sanitize và che giấu bí mật (`[REDACTED]`) đối với các trường OTP, token, secret, password trong log.
6. **Internal Security API Key:**
   - Toàn bộ route `/internal/v1/**` bảo vệ nghiêm ngặt qua header `X-Internal-Api-Key`.

---

## 2. Tiêu chí nghiệm thu (Acceptance Criteria Evidence Matrix)

| Tiêu chí | Kịch bản kiểm thử (Test Scenario) | Kết quả nghiệm thu | Test Case |
| :--- | :--- | :--- | :--- |
| **Event trùng không tạo notification trùng** | 2 request gửi cùng `Idempotency-Key` (ví dụ mobile retry khi mạng chập chờn). | Chỉ tạo đúng 1 notification trong DB; Push Provider chỉ gọi đúng 1 lần. | `NotificationIdempotencyTests` (PASS) |
| **Logout disable token** | User logout -> vô hiệu hóa tất cả token. Sau đó bạn bè đăng ảnh widget mới. | Toàn bộ token `active = false`; không gửi push tới thiết bị cũ. | `DeviceTokenLifecycleTests` (PASS) |
| **Email không lộ token trong log** | Gửi email chứa OTP/secret/token nhạy cảm. | Log hệ thống tự động ẩn thành `[REDACTED]`; không tồn tại token thô trong log. | `EmailSecurityLoggingTests` (PASS) |
| **Delivery failure retry giới hạn** | Giả lập lỗi mạng tạm thời liên tục khi push. | Thử lại tối đa 3 lần (`max_attempts = 3`); lần thứ 3 chuyển trạng thái `EXHAUSTED` và dừng hẳn. | `DeliveryRetryLimitTests` (PASS) |

---

## 3. Bằng chứng kiểm thử tự động (Automated Test Evidence)

```sh
mvn test -pl notification-service
```

```
[INFO] Running com.nhantr.notification_service.controller.InternalSecurityApiKeyTests
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nhantr.notification_service.controller.NotificationControllerTests
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nhantr.notification_service.email.EmailSecurityLoggingTests
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nhantr.notification_service.email.EmailTemplateRenderingTests
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nhantr.notification_service.entity.NotificationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nhantr.notification_service.service.DeliveryRetryLimitTests
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nhantr.notification_service.service.DeviceTokenLifecycleTests
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nhantr.notification_service.service.NotificationIdempotencyTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nhantr.notification_service.service.NotificationInboxTests
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 4. Definition of Done (DoD) Checklist

- [x] **PR gắn card:** Nhánh `feature/LC-S3-03-—-Notification-Service-In-app-Push-Adapter-và-Email` sẵn sàng tạo PR vào `dev`.
- [x] **CI xanh:** Toàn bộ test suite 20/20 test Notification Service và 18/18 test Chat Service đều PASS.
- [x] **Test evidence:** Đầy đủ bằng chứng nghiệm thu 4 tiêu chí cốt lõi trong báo cáo này.
- [x] **Docs/OpenAPI cập nhật:** Đã cập nhật `backend/docs/03-api-catalog.md` với đầy đủ các endpoint mới và contract.
- [x] **Không còn lỗi P0/P1:** 100% checklist và acceptance criteria của LC-S3-03 đã hoàn thành.
