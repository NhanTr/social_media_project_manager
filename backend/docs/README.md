# Tài liệu kỹ thuật LocCoc Backend

Bộ tài liệu này được tổng hợp từ thiết kế LocCoc trên Notion và đối chiếu với source code tại repository ngày 2026-07-29.

## Cách đọc

1. [Kiến trúc hệ thống](01-system-architecture.md): bức tranh tổng thể, ranh giới public/internal và nguyên tắc dữ liệu.
2. [Luồng hoạt động](02-business-flows.md): các luồng end-to-end từ đăng nhập đến moderation.
3. [Danh mục API](03-api-catalog.md): API public, internal, WebSocket, request/response và phân quyền.
4. [Chức năng từng service](04-service-responsibilities.md): ownership, dependency và trách nhiệm của từng module.
5. [Khoảng trống và API cần bổ sung](05-gaps-and-roadmap.md): khác biệt giữa thiết kế, code hiện tại và đề xuất ưu tiên.

## Quy ước trạng thái

- **Đã có**: endpoint hoặc chức năng tồn tại trong source code.
- **Một phần**: đã có contract hoặc mock nhưng chưa có tích hợp production.
- **Cần bổ sung**: có trong use case/thiết kế nhưng chưa có API hoặc chưa hoàn chỉnh.
- **Client-side**: thuộc Swift app/OS, backend chỉ cung cấp dữ liệu hỗ trợ.

## Nguồn thiết kế

- [Trang tổng quan LocCoc](https://app.notion.com/p/3947b662e4cd80359960e81fc888317e)
- [Danh sách use case](https://app.notion.com/p/d83d4bba0fbc49d3a3933802efdcb90f)
- [Use case chi tiết](https://app.notion.com/p/a02b8343b8e1474081baafdb213066c2)
- [Thiết kế cơ sở dữ liệu](https://app.notion.com/p/7aa8cdfd0aed4d05a859d3037c5825cb)
- [Kiến trúc và sprint plan](https://app.notion.com/p/e1c15a30f6534d32b2bb13bb99af0514)
- [Thiết kế API Gateway](https://app.notion.com/p/39a7b662e4cd80d789d2e440404c17e7)

> Khi tài liệu Notion và code khác nhau, bộ tài liệu này mô tả code hiện tại trước, sau đó ghi phần cần bổ sung riêng.
