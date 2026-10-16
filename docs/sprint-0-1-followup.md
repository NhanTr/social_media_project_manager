# Bổ sung Sprint 0–1 — 16/10/2026

## Phần thực hiện

- LC-S1-01: OTP cooldown 60 giây, giới hạn 5 lần thử/TTL, Lua consume nguyên tử; refresh GETDEL chống hai request dùng cùng refresh token.
- LC-S1-02: sửa otp/devOtp, Google Sign-In SDK (cần OAuth config), secure storage và dọn token plaintext cũ; 401 retry có giới hạn, timeout request; Terms tải lỗi không consent nhầm ID mặc định, reset loading sau consent; widget routing dùng HTTP/storage fake.
- LC-S1-03/04: CANCELLED migration + cancel API chỉ sender, retry idempotent, khóa hàng accept/decline/cancel; nút hủy outgoing; model friend/blocked đúng API; khóa thao tác theo user/request, rollback không tạo request giả; lỗi tải có retry và bỏ kết quả tìm kiếm cũ.
- LC-S0-02/03: context/dependency diagram, giải thích hiện trạng schema/boundary; environment API_BASE_URL/APP_ENV; README chạy local và OAuth/secure storage.
- LC-S0-04: format check, timeout test/job, concurrency hủy run cũ, migration DB rỗng/validate, artifacts test/coverage, Conventional PR title.

## Phần vẫn chờ

Demo Android/iOS và toàn stack; Google OAuth credentials/URL scheme; Terms gate server cho API nghiệp vụ; race block với accept; quyết định sáu boundary/schema; dependency audit; required checks/PR lỗi bị chặn; người review và merge PR. Access token chưa bị revoke tức thì khi logout; refresh token đã revoke.

## Nghiệm thu gọn

1. Chạy migration V3, mở app và dùng hai tài khoản OTP.
2. Consent → profile → tìm bạn → gửi → hủy; B không còn incoming.
3. Gửi lại → accept; tìm lại phải hiển thị đã là bạn. Block/unblock và kiểm tra danh sách.
4. Double tap một action; không gửi hai mutation. Tắt mạng, thử lại và kiểm tra UI báo lỗi.
5. Logout, đóng/mở app; về login. Không còn token trong SharedPreferences.
6. Xem CI trên PR. Chỉ tick mục nghiệm thu đã có bằng chứng.

Rollback: revert commit qua PR; không sửa V3 đã chạy, không drop enum/volume. Enum CANCELLED có thể giữ khi rollback code; kiểm tra dữ liệu trước khi rollback service cũ.

## Kiểm tra local đã chạy

- Flutter analyze: không có issue.
- Flutter test: 7/7 pass, timeout 30 giây/test; gồm OTP contract, social flags, cancel/double-submit, secure storage/logout và routing widget.
- Maven test: 10 module SUCCESS, 11 tests không lỗi. Test cancel gồm retry sender và từ chối receiver/request đã accepted.
- Mockito social dùng subclass mock maker để không phụ thuộc JVM self-attach trong sandbox.
- Git diff --check: pass. Migration/runtime và CI remote ghi nhận riêng trên PR.
