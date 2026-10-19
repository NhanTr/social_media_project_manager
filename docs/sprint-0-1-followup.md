# Bổ sung Sprint 0–1 — cập nhật 19/10/2026

## Phần thực hiện

- LC-S1-01: OTP cooldown 60 giây, giới hạn 5 lần thử/TTL, Lua consume nguyên tử; refresh GETDEL chống hai request dùng cùng refresh token.
- LC-S1-01: access token có claim `terms_accepted`; token cũ thiếu claim được xử lý an toàn như chưa đồng ý. API Gateway trả `403 TERMS_ACCEPTANCE_REQUIRED` cho API nghiệp vụ và chỉ miễn các route đăng nhập/refresh/đọc-chấp-nhận điều khoản, actuator và OpenAPI.
- LC-S1-01: audit xác thực có cấu trúc cho OTP, Google, refresh, logout và consent; chỉ ghi event/outcome/user ID/request ID, không ghi OTP, token, email, số điện thoại hay Google credential.
- LC-S1-02: sửa otp/devOtp, Google Sign-In SDK (cần OAuth config), secure storage và dọn token plaintext cũ; 401 retry có giới hạn, timeout request; Terms tải lỗi không consent nhầm ID mặc định. Sau consent, app bắt buộc refresh access token mới rồi mới vào phiên đăng nhập; refresh lỗi giữ màn Terms để retry.
- LC-S1-03/04: CANCELLED migration + cancel API chỉ sender, retry idempotent, khóa hàng accept/decline/cancel; nút hủy outgoing; model friend/blocked đúng API; khóa thao tác theo user/request, rollback không tạo request giả; lỗi tải có retry và bỏ kết quả tìm kiếm cũ.
- LC-S1-03: `block` khóa bi quan toàn bộ request pending của cặp người dùng trước khi đổi trạng thái, xóa friendship và tạo block. Test PostgreSQL 16 chạy hai giao dịch block/accept đồng thời và xác nhận bất biến cuối `blocked && !friends && !pending`.
- LC-S0-02/03: context/dependency diagram, giải thích hiện trạng schema/boundary; environment API_BASE_URL/APP_ENV; README chạy local và OAuth/secure storage.
- LC-S0-04: format check, timeout test/job, concurrency hủy run cũ, migration DB rỗng/validate, artifacts test/coverage, Conventional PR title.

## Bằng chứng theo commit

| Commit | Phạm vi | Bằng chứng tự động |
| --- | --- | --- |
| `07f6474` | JWT mang trạng thái consent | JWT/common/chat tests xác minh claim mới và fallback token cũ. |
| `8c32365` | Terms gate tại Gateway | 5 filter tests cho route protected, route exempt, token thiếu/sai claim và response 403. |
| `5b9c2f9` | Refresh phiên sau consent trên Flutter | Widget/controller test xác minh refresh thành công mới rời Terms; refresh lỗi vẫn retry được. |
| `f4c368b` | Audit đăng nhập đã redaction | 8 audit tests xác minh event thành công/thất bại và không lộ credential/PII. |
| `a5144eb` | Tuần tự hóa block với accept | Unit test lock/idempotency và integration test PostgreSQL 16 với hai transaction đồng thời. |

## Phần vẫn chờ

- Demo Android và iOS trên thiết bị/emulator.
- Chạy toàn stack và kiểm tra luồng end-to-end với hai tài khoản thật.
- Cấu hình Google OAuth credentials và URL scheme, sau đó kiểm tra đăng nhập Google thực tế.
- Chốt sáu quyết định boundary/schema còn mở và dependency audit.
- Nhờ một người review PR #2; hiện PR chưa có review và không có conversation mở.
- Bật hoặc thống nhất chính sách branch protection/required checks cho `dev`; API GitHub hiện báo nhánh chưa được bảo vệ.
- Thực hiện merge PR sau khi các cổng thủ công trên được đối chiếu.
- Access token hiện tại chưa bị revoke tức thì khi logout; refresh token đã revoke. Đây vẫn là giới hạn được ghi nhận, không được mô tả là đã xử lý.

## Nghiệm thu gọn

1. Chạy migration V3, mở app và dùng hai tài khoản OTP.
2. Consent → profile → tìm bạn → gửi → hủy; B không còn incoming.
3. Gửi lại → accept; tìm lại phải hiển thị đã là bạn. Block/unblock và kiểm tra danh sách.
4. Double tap một action; không gửi hai mutation. Tắt mạng, thử lại và kiểm tra UI báo lỗi.
5. Logout, đóng/mở app; về login. Không còn token trong SharedPreferences.
6. Xác minh API nghiệp vụ trả 403 trước consent, consent xong refresh token thành công rồi mới truy cập được.
7. Cho accept và block chạy gần đồng thời; trạng thái cuối phải blocked, không friendship, không pending request.
8. Xem CI trên PR. Chỉ tick mục nghiệm thu đã có bằng chứng.

Rollback: revert commit qua PR; không sửa V3 đã chạy, không drop enum/volume. Enum CANCELLED có thể giữ khi rollback code; kiểm tra dữ liệu trước khi rollback service cũ.

## Kiểm tra local đã chạy

- `mvn test` từ `backend/`: BUILD SUCCESS, 10/10 module SUCCESS, tổng 32 test, 0 failure, 0 error, 0 skipped. Bao gồm PostgreSQL 16 Testcontainers cho race block/accept.
- `flutter analyze`: không có issue.
- `flutter test`: 10/10 pass; gồm OTP contract, social flags, cancel/double-submit, secure storage/logout, routing và refresh sau consent.
- `docker compose config --quiet` từ `backend/`: pass (exit 0).
- `git diff --check`: pass (exit 0).
- GitHub Actions run `39180565014` trên PR #2 sau commit sửa formatter `3457534`: Backend Build & Unit Tests, Flutter Lint & Unit Tests, Database Migration và Security Scan & Conventions đều pass.
- PR #2 được GitHub đánh giá `CLEAN` và `MERGEABLE`, không có review conversation mở. Trạng thái này không thay thế người review, kiểm tra thiết bị/OAuth/full-stack hoặc quyết định merge của người phụ trách.
