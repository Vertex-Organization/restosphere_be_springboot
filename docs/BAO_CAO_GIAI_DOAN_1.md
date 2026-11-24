# Báo cáo Giai đoạn 1 – RestoSphere

Ngày: 2026-11-23. Nhánh backend: `refactor/db-flyway-payos`.

## Phạm vi đã triển khai

| Hạng mục | Commit |
|---|---|
| Thêm Flyway | `5ee8b4c` |
| User → Employee, bỏ role CUSTOMER | `aa8fbbd` |
| Người thực hiện là FK, bill liên kết yêu cầu duyệt | `e213aa6` |
| Gộp giỏ món vào orders DRAFT/SUBMITTED | `fd70c7e` |
| Khu active/sort_order, đặt bàn không yêu cầu khu | `fe0fad2` |
| table_sessions.bill_id thay bills.session_id | `ca4f0d7` |
| Payments mới và chuyển khoản thủ công | `34f4c18` |a
| Ghép/tách phiên và các luật chặn | `ed7c60c` |
| BIN ngân hàng | `4c7a765` |
| V1 schema và Hibernate validate | `10d7816` |

Seed đang được chuyển từ `dataset.sql` sang `db/seed/V1000__dev_seed.sql`, chỉ thêm location ở profile dev. Thay đổi seed và test liên quan chưa commit tại thời điểm lập báo cáo.

## Ảnh hưởng API và frontend

- `/api/v1/admin/users` đổi thành `/api/v1/admin/employees`.
- Phương thức thanh toán: CASH, PAYOS, MANUAL_TRANSFER.
- Bỏ luồng transfer chờ xác nhận; thêm `/api/v1/cashier/bills/{id}/payments/manual-transfer`.
- Bỏ lệnh lễ tân `confirm-deposit`.
- Thêm `/api/v1/table-sessions/{id}/merge` và `/split`.
- Settings có `billing.bankBin`, cấu hình thu ngân có `bank.bin`.
- Khu có active/sortOrder; reservation có thể không yêu cầu khu.
- FE web và mobile chưa cập nhật; chưa được coi là đợt nâng cấp hoàn chỉnh để triển khai cho người dùng.

## Database

V1 có 24 bảng nghiệp vụ, tên constraint rõ ràng và ba partial unique index. Đã chạy V1 trên database thử nghiệm `restosphere_v2`; Hibernate validate và bộ test đã chạy thành công ở bước V1. Không dùng database `restosphere` để chạy test tích hợp.

Đã bỏ `defer-datasource-initialization`, vì cấu hình này khiến Hibernate validate trước Flyway. DatabaseConstraintsInitializer được thay bằng migration.

## Kiểm tra

- Sau bank_bin/V1: 202 test pass theo kết quả chạy trước đó.
- Test riêng profile dev đã nạp seed qua Flyway; test gọi seed trực tiếp cần transaction vì bảng tạm ON COMMIT DROP.
- Đã chạy lại `spotless:apply` và toàn bộ test sau khi sửa transaction; cần đối chiếu báo cáo Surefire trước khi ghi tổng kết cuối hoặc commit seed.
- Chưa kiểm thử giao diện hoặc giao dịch payOS thật.

## Các phần còn thiếu và rủi ro phải xử lý

Không coi toàn bộ nghiệp vụ Giai đoạn 1 đã nghiệm thu chỉ vì các test hiện có pass:

1. Xác thực quản lý khi chuyển khoản thủ công chưa có giới hạn thử sai như login; cần sửa trước khi phát hành.
2. Ghép/tách chưa giới hạn phục vụ theo bàn phụ trách. Tách đặt số khách mới là 1, cần xác nhận và phân bổ số khách đúng.
3. Chưa hoàn thiện mở nhiều phiên cho reservation nhiều bàn và đóng toàn bộ phiên/trả bàn khi bill PAID.
4. Chưa có luồng thu cọc tiền mặt/thủ công hoàn chỉnh tại thu ngân, xác nhận cọc tự động và đối soát trước khi hủy đặt bàn; tiếp tục trong Giai đoạn 2.
5. Seed cần rà soát các MANUAL_TRANSFER không COMPLETED và người xác nhận; V1000 có nguy cơ khiến V2 bị xem là migration cũ khi chạy trên DB đã seed, cần quyết định cách version trước khi chốt.
6. Cấu hình dev chưa đảm bảo luôn trỏ tới restosphere_v2; không chạy app với mặc định trên DB cũ.
7. `orders.submitted_at` là bổ sung ngoài bản chốt, đang chờ xác nhận.

## Bắt đầu Giai đoạn 2

Đọc API/source SDK `vn.payos:payos-java:2.0.1` trước khi viết tích hợp. Thứ tự: cấu hình SDK; xử lý trạng thái dùng chung và idempotent; link bill/cọc; webhook và kiểm tra lại; hết hạn cọc; endpoint trạng thái an toàn cho khách. Không đưa khóa xuống frontend, không bật endpoint giả lập ngoài profile dev.
