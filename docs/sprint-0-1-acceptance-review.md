# Rà soát và hướng dẫn nghiệm thu Sprint 0–1

Cập nhật 15/10/2026. Source được kiểm tra: `5071e6b`. Đây là báo cáo kiểm tra, không phải biên bản xác nhận hai sprint đã Done.

## Bằng chứng đã chạy

- `mvn -B -o test`: 10 module SUCCESS, 9 tests, không failure/error. Các test hiện chủ yếu kiểm tra JWT/entity và Gateway context; chưa chứng minh toàn bộ luồng nghiệp vụ.
- `flutter analyze`: No issues found.
- `flutter test`: 2 test đầu pass; test routing thứ ba chưa hoàn tất sau khoảng 62 giây và đã được dừng thủ công. Lỗi shutdown xuất hiện sau khi dừng; không kết luận toàn bộ suite pass hoặc đã xác định nguyên nhân treo.
- `docker compose config --quiet`: pass. `docker compose ps` không có container; chưa nghiệm thu stack runtime, migration DB rỗng hoặc Android/iOS.
- Khi kiểm tra GitHub: main/dev ở `98e3939`, chưa có remote tag baseline; chưa có Actions run. Main yêu cầu một review nhưng `required_status_checks=null`.

## Đối chiếu từng task

| Task | Commit triển khai | Phần đã có | Phần còn thiếu/chưa nghiệm thu |
|---|---|---|---|
| LC-S0-01 | 9cabc2f | Source backend, Maven aggregator, inventory, tag local | Publish/review/merge, clean clone build tag, sửa thông tin inventory và README cũ |
| LC-S0-02 | 81e7f7b | ADR, ownership, quy tắc auth, ngoài phạm vi | Context/dependency diagram, contract review; thống nhất 6 boundary kế hoạch với 7 domain triển khai, Subscription chưa có; tài liệu nói schema riêng nhưng migration hiện tạo bảng không định danh schema |
| LC-S0-03 | 9269ab7 + 9cabc2f | Flutter structure/networking, Gateway/backend, Compose | Demo Android/iOS, toàn bộ service health, app gọi Gateway; token storage hiện dùng SharedPreferences |
| LC-S0-04 | 7f54ca8 | Workflow Flutter/Maven, PR template | Migration CI, artifact upload, kiểm tra format/convention/dependency, required checks và PR đỏ bị chặn merge |
| LC-S1-01 | 5071e6b + 9cabc2f | Auth/profile/Terms/session backend | OTP resend limit, integration refresh/logout/concurrency; kiểm tra Terms gate ở API nghiệp vụ |
| LC-S1-02 | 5071e6b | Auth screens, routing, refresh single-flight, logout local | OTP request gửi otpCode thay vì otp; response đọc otpCode thay vì devOtp; Google UI gửi token giả cho verifier thật; secure storage; routing test chưa hoàn tất |
| LC-S1-03 | 5071e6b + 9cabc2f | Send/accept/decline, friends, block/unblock, internal checks | Cancel request chưa có; test concurrency/idempotency; chat history chỉ kiểm tra participant, cần xác nhận yêu cầu đọc lịch sử sau block |
| LC-S1-04 | 5071e6b | Search debounce, pending lists, accept/decline/block UI | Model đọc areFriends/isBlocked nhưng API trả friend/blocked; cancel UI; chống double tap; error/retry và integration hai tài khoản |

Một số phần backend có từ baseline `9cabc2f`; không quy toàn bộ backend Sprint 1 cho commit cuối. Commit Sprint 1 hiện gộp bốn task; giữ nguyên lịch sử và liên kết cùng commit trên cả bốn thẻ.

## Cách nghiệm thu

### 1. Baseline và kiến trúc — LC-S0-01/02

Sau khi code được push, clone vào thư mục mới và checkout đúng commit/tag; chạy Maven và Flutter như dưới. Ghi SHA đầy đủ, log, người kiểm tra. Nhóm A/B/C walkthrough login → Terms → friend → post/chat, đối chiếu API thực tế và data ownership. Chốt bằng văn bản mọi khác biệt kiến trúc trước khi tick nghiệm thu.

### 2. Build và runtime — LC-S0-03

Chạy tại thư mục backend:

```sh
mvn test
docker compose config --quiet
docker compose up --build -d
docker compose ps -a
curl -f http://localhost:8080/actuator/health
```

Kỳ vọng Flyway exit 0, Gateway và bảy domain service healthy. Lưu log Flyway và health từng cổng 8080–8087. Migration DB rỗng phải dùng môi trường thử nghiệm tách biệt; không xóa volume dữ liệu hiện tại.

Chạy tại thư mục loccoc_application:

```sh
flutter analyze
flutter test
flutter devices
flutter run -d <device-id>
```

Thực hiện riêng Android emulator và iOS simulator. Android dùng host 10.0.2.2:8080, iOS simulator localhost:8080 theo AppConfig. Quay màn hình app kết nối Gateway. Nếu test chưa kết thúc hoặc app chưa gọi API được, để mục đó chưa đạt.

### 3. CI và quy trình Git — LC-S0-04

Mở PR vào dev, gắn card và evidence. Bổ sung các job thiếu, chọn đúng required checks cho nhánh nhận merge. Tạo PR thử nghiệm có một assertion sai, chụp CI đỏ và merge bị chặn; sửa assertion, xác nhận CI xanh. Không merge lỗi thử nghiệm. Lưu link hai run và artifact; CI xanh một mình chưa thay thế demo nghiệp vụ.

### 4. Auth/profile/Terms — LC-S1-01/02

Dùng hai tài khoản thử A/B qua Gateway. Với Postman hoặc Swagger, thứ tự:

1. POST `/api/v1/auth/phone/send-otp` với `{"phoneNumber":"+84900000001"}`. Local bật expose-code trả trường `devOtp`.
2. POST `/api/v1/auth/phone/verify-otp` với `{"phoneNumber":"+84900000001","otp":"<devOtp>","displayName":"Test A"}`. Không gửi trường otpCode. Lưu token trong biến local, không đưa token vào evidence.
3. User mới phải vào Terms; lấy GET `/api/v1/terms/latest`, POST `/api/v1/terms/accept` với termsVersionId thực tế. Trước consent, thử API nghiệp vụ và xác nhận chính sách chặn.
4. GET và PUT `/api/v1/users/me`: sửa displayName, khởi động lại app và xác nhận giữ đúng giá trị; field trái phép không thay đổi quyền/tài khoản.
5. POST `/api/v1/auth/refresh` với refreshToken R1: nhận R2; dùng lại R1 phải bị từ chối. Thử hai request đồng thời để kiểm tra rotation an toàn.
6. POST `/api/v1/auth/logout` với R2; R2 không refresh được nữa. App xóa session; mở lại về login. Ghi rõ chính sách access token còn hiệu lực đến expiry hay revoke tức thì.
7. Test token hết hạn, nhiều API đồng thời (một refresh), API vẫn 401 sau retry (không lặp vô hạn), offline và khôi phục mạng.
8. Sửa các lỗi contract nêu trên rồi thực hiện lại toàn bộ qua UI. Google login chỉ nghiệm thu khi có ID token hợp lệ và client ID phù hợp; token giả hiện tại không chứng minh luồng Google hoạt động.

### 5. Friends/block — LC-S1-03/04

1. A tìm username B → gửi lời mời. A thấy outgoing, B thấy incoming.
2. B accept → cả hai thấy friendship; tìm lại B phải hiển thị đúng trạng thái friend.
3. Dùng cặp test mới để thử decline và cancel. Cancel hiện thiếu nên đánh dấu chưa đạt.
4. Gửi self-request, gửi trùng, double tap và hai request đồng thời: không tạo quan hệ trùng; UI không bị nhân bản hoặc rollback sai.
5. A block B → friendship/pending biến mất; API post và gửi chat bị chặn. Thử cả hai chiều và hội thoại đã mở sẵn. Xác nhận thêm chính sách lịch sử chat sau block.
6. Unblock → bỏ block nhưng không tự khôi phục friendship; có thể gửi lời mời mới.
7. Tắt mạng khi tải và khi thao tác: thông báo lỗi, retry hoạt động; kiểm tra nhãn accessibility của các nút chính.

## Mẫu evidence trên mỗi card

```text
Task:
Commit SHA / PR:
Môi trường và thiết bị:
Người kiểm tra / thời điểm:
Case / bước thực hiện:
Kỳ vọng:
Kết quả thực tế: PASS / FAIL / CHƯA CHẠY
Log / video (đã che token, OTP):
Lỗi còn mở:
Người review:
```

Chỉ chuyển cả card sang Done khi PR đã merge, CI và tiêu chí nghiệm thu đạt. Checklist “đã triển khai” chỉ xác nhận phần code/tài liệu tồn tại và được kiểm tra tương ứng.
