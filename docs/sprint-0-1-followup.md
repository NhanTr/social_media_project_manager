# Bổ sung Sprint 0–1 — cập nhật 21/10/2026

## Quyết định close-out MVP

- MVP chấp nhận schema `public` dùng chung với ownership/grants.
- Schema riêng theo service được hoãn sang sprint hậu MVP.
- Mock/demo không production-ready.
- Adapter production AI, payment, email và push được hoãn sang Icebox.

Hiện trạng được chấp nhận chỉ cho MVP/demo: một PostgreSQL cluster, Flyway dùng
chung và quyền bảng theo service owner. Đích hậu MVP là schema/credential riêng,
kiểm thử migration và restore theo service. AI hiện là luồng mock; payment và
email chưa có adapter production; push chưa kết nối FCM/APNs production. Các
mục này vẫn để chưa hoàn thành trên Trello và cần task/sprint riêng.

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
| `6d74a6d` | Environment dev/staging/prod | Dart config tests, Python sample validator, analyzer và Flutter tests đạt. |
| `ecb5ff9`–`37f1175` | CI quality gates | Conventional commit, Checkstyle, Flutter APK, migration và Trivy HIGH/CRITICAL; CI run `39467303520` xanh. |
| `b8c2897` | Loading/error/retry dùng chung | 23 Flutter tests; mutation không tự replay và session expiry vẫn về login. |
| `85ad719` | Search phone hash chính xác | 17 identity tests; PostgreSQL 16 migrate/validate V4 và backfill canonical SHA-256 đạt. |

## Phần vẫn chờ

- Demo Android và iOS trên thiết bị/emulator.
- Chạy toàn stack và kiểm tra luồng end-to-end với hai tài khoản thật.
- Cấu hình Google OAuth credentials và URL scheme, sau đó kiểm tra đăng nhập Google thực tế.
- Lập kế hoạch hậu MVP cho schema-per-service và adapter production đã hoãn.
- Nhờ một người review PR #2; hiện PR chưa có review và không có conversation mở.
- Bật hoặc thống nhất chính sách branch protection/required checks cho `dev`; API GitHub hiện báo nhánh chưa được bảo vệ.
- Thực hiện merge PR sau khi các cổng thủ công trên được đối chiếu.
- Access token hiện tại chưa bị revoke tức thì khi logout; refresh token đã revoke. Đây vẫn là giới hạn được ghi nhận, không được mô tả là đã xử lý.

## Nghiệm thu gọn

1. Chạy migration V4, mở app và dùng hai tài khoản OTP.
2. Consent → profile → tìm bạn → gửi → hủy; B không còn incoming.
3. Gửi lại → accept; tìm lại phải hiển thị đã là bạn. Block/unblock và kiểm tra danh sách.
4. Double tap một action; không gửi hai mutation. Tắt mạng, thử lại và kiểm tra UI báo lỗi.
5. Logout, đóng/mở app; về login. Không còn token trong SharedPreferences.
6. Xác minh API nghiệp vụ trả 403 trước consent, consent xong refresh token thành công rồi mới truy cập được.
7. Cho accept và block chạy gần đồng thời; trạng thái cuối phải blocked, không friendship, không pending request.
8. Xem CI trên PR. Chỉ tick mục nghiệm thu đã có bằng chứng.

Rollback: revert code qua PR; không sửa V3/V4 đã chạy và không drop enum, cột
hash hoặc volume. Enum `CANCELLED` và `phone_lookup_hash` có thể giữ khi rollback
service; kiểm tra dữ liệu và compatibility trước khi quay về bản cũ.

## Kiểm tra local đã chạy

- `python3 -m unittest discover -s scripts/tests -v`: 16/16 pass, gồm environment, conventional commit, CI contract và đồng bộ tài liệu close-out.
- `mvn -B clean verify` từ `backend/`: BUILD SUCCESS, 10/10 module SUCCESS, 38 test, 0 failure, 0 error, 0 skipped; `mvn -B -DskipTests checkstyle:check` có 0 violation.
- PostgreSQL 16.14: Flyway migrate V1–V4 và validate thành công; ba fixture dạng `090...`, `84...`, `+84...` có hash 64 ký tự khớp canonical SHA-256.
- `flutter analyze`: không có issue; `flutter test --coverage`: 23/23 pass, line coverage 489/1220 (40,1%).
- Debug APK build thành công tại `loccoc_application/build/app/outputs/flutter-apk/app-debug.apk` (177 MB tại thời điểm kiểm tra).
- `docker compose config --quiet` và `git diff --check`: pass (exit 0).
- GitHub Actions run `39526673095` trên PR #3 tại commit `85ad719`: Backend, Flutter, Database Migration và Security Scan & Conventions đều pass, gồm migration V4 và Trivy HIGH/CRITICAL.
- PR #3 vẫn là Draft và chưa merge. Trạng thái tự động không thay thế reviewer, tester, kiểm tra thiết bị/OAuth/full-stack hoặc quyết định merge của người phụ trách.
