# RestoSphere Refactor – Tiến độ

## Trạng thái
- Nhánh: refactor/db-flyway-payos (BE: restosphere-be-springboot; FE web: restophere-fe-web, nhánh develop)
- Giai đoạn hiện tại: 1
- Cập nhật lần cuối: 2026-11-23 18:50

## Đang làm
- GĐ1: bước "Gộp order_drafts vào orders (DRAFT/SUBMITTED)"

## Câu hỏi đang chờ
- [ ] FE mobile (`restosphere-fe-mobile`, Expo) chứa màn lễ tân + phục vụ – master prompt chỉ nêu FE web. Tạm: Giai đoạn 3 làm FE web trước, mobile chỉ sửa chỗ vỡ API nếu còn thời gian; ghi TODO(confirm). (2026-11-23)
- [ ] Màn hình lễ tân (tạo đặt bàn quầy/hotline, Thu cọc, copy link) và phục vụ (giỏ DRAFT) hiện nằm ở mobile, FE web chưa có. Tạm: tạo trang lễ tân web mới đồng bộ style manager/billing. (2026-11-23)
- [ ] areas: thêm cột `active` và `sort_order`? (chưa chốt) – tạm KHÔNG đổi `areas`. (2026-11-23 18:50)
- [ ] `reservations.area_id` cho phép NULL (khách không chọn khu)? – tạm KHÔNG đổi. (2026-11-23 18:50)
- [ ] Seed dev: giữ `dataset.sql` (SQL init) hay chuyển sang Flyway `db/seed`? – tạm giữ, chỉnh theo schema mới. (2026-11-23 18:50)

## Quyết định đã nhận từ người dùng trong lúc làm
- (chưa có)

## Danh sách công việc
### Giai đoạn 0
- [x] Tạo nhánh + file tiến độ
- [x] Đọc 2 skill FE (tại restophere-fe-web/.agents/skills)
- [x] So entity với DB thực tế
- [x] Kiểm tra ImmutableEntity / RefreshToken
- [x] Kiểm tra luồng refresh token BE + FE
- [x] Kiểm tra Bill-TableSession, cascade, enum dùng chung
- [x] Liệt kê màn hình FE + API bị ảnh hưởng
### Giai đoạn 1
- [x] Flyway setup — commit `5ee8b4c` (dependency thêm, enabled=false tới khi có V1)
- [x] User -> Employee, bỏ role CUSTOMER — commit `aa8fbbd` (endpoint đổi `/api/v1/admin/users` -> `/api/v1/admin/employees`, FE phải cập nhật ở GĐ3; JSON field không đổi)
- [x] Cột người thực hiện -> FK + bills.approval_request_id — commit `e213aa6` (JSON vẫn trả id dạng chuỗi, FE không vỡ)
- [ ] Gộp order_drafts vào orders
- [ ] Phiên-bill: table_sessions.bill_id, ghép/tách
- [ ] Payments: cột mới, method mới, cọc, MANUAL_TRANSFER, chặn đóng ca
- [ ] restaurant_settings.bank_bin
- [ ] V1 migration + validate
### Giai đoạn 2
- [ ] Cấu hình SDK payOS
- [ ] Link payOS cho bill + hủy link
- [ ] Link cọc (online, hotline, quầy)
- [ ] Cọc tiền mặt / MANUAL_TRANSFER (Cọc chờ thu)
- [ ] Kiểm tra lại + job hết hạn cọc
- [ ] Webhook + endpoint dev
- [ ] Endpoint tra trạng thái cho FE
### Giai đoạn 3
- [ ] (xem mục 5 của master prompt)
### Giai đoạn 4
- [ ] Seed, README, tự kiểm thử, QUY_TRINH_SU_CO.md, tóm tắt payOS

## Kết quả rà soát (Giai đoạn 0)
### Tổng quan
- BE: 349 file; entity kế thừa `BaseEntity` (id UUIDv7 + created_at + updated_at). 25 bảng trong DB `restosphere` (ddl-auto=update). Controller dưới `/api/v1`. Có "workspace" (`WorkspaceService/Store/Snapshots/Commit`) trả snapshot + lệnh cho FE staff.
- FE web: `D:\Project\RestoSphere\restophere-fe-web` (React 19 + **TypeScript**, Vite, CSS thuần; không có Tailwind). Có `.agents/skills/` (2 skill), nhánh `develop`.
- FE mobile: `D:\Project\RestoSphere\restosphere-fe-mobile` (Expo/React Native, TS) cho lễ tân + phục vụ; nhánh `develop`.
- Skill `design-taste-frontend` chủ yếu cho landing page; áp dụng cho màn nội bộ: bám token/style có sẵn của từng khu (billing.css, manager.css, kitchen.css), không thêm hiệu ứng/ảnh. `clone-website`: không áp dụng được trực tiếp (không clone site nào); dùng tinh thần "bám sát style sẵn có".

### So entity với DB thực tế
- Sinh schema từ entity vào DB tạm rồi so `information_schema`: **cột, kiểu, độ dài, nullable khớp 100%**; không có cột mồ côi, không lệch kiểu. DB tạm đã xóa.
- Chỉ lệch tên constraint (DB thực dùng `*_key`, Hibernate dùng `uk<hash>`) – không ảnh hưởng, V1 sẽ đặt tên rõ ràng.
- Index một phần `uq_cashier_shifts_open_per_cashier`, `uq_table_sessions_open_per_table` đang tạo bằng `DatabaseConstraintsInitializer` (ApplicationRunner) → chuyển vào V1 và xóa class này.
- DB thực: 25 bảng, đúng bằng số entity.

### ImmutableEntity / RefreshToken
- `ImmutableEntity` chỉ có `id` + `created_at(updatable=false)`; **không** dùng `@Immutable` → update các field khác không bị chặn.
- `RefreshToken` **không** kế thừa `ImmutableEntity` (tự khai id/created_at) → `revoked_at` update được. Không có vấn đề.
- Kế thừa `ImmutableEntity`: AuditLog, NotificationRead, ReservationTable, ShiftAssignment.

### Refresh token
- BE: `POST /api/v1/auth/refresh` (+login/logout), `RefreshTokenService`, permitAll qua `TOKEN_ENDPOINTS`. FE web `src/api/auth.ts` có refreshSession + retry 1 lần khi 401. Đang dùng → giữ, chỉ đổi `user_id` → `employee_id`.
- Mobile `features/auth/api.ts` cũng gọi auth (chưa kiểm tra chi tiết refresh).

### Bill ↔ TableSession và cascade
- `Bill.session` = `@OneToOne(optional=false)` + `session_id` unique, **một chiều** (TableSession không có field bill). Sẽ đảo: `TableSession.bill` `@ManyToOne` (NOT NULL), xóa `Bill.session`.
- Không có `cascade`/`orphanRemoval`/`OneToMany`/`mappedBy` ở bất kỳ entity nào → mọi quan hệ chỉ là ManyToOne/OneToOne một chiều, lưu thủ công qua `WorkspaceStore`. Không rủi ro cascade khi đổi.
- `TableSession.reservation` `@OneToOne unique` → bỏ unique, đổi `@ManyToOne`.
- Bill tạo ở `ReservationWorkflowImpl:668-675` (khi check-in/mở phiên từ reservation) – lúc đó tạo session rồi bill; phải đảo thứ tự. Chưa thấy chỗ khác `new Bill()`.
- `BillingWorkflowImpl.bill(session)` tra `Bill where session=?` và `items(bill)` lấy item theo `order.session` → phải đổi sang tra qua `session.bill` và item của mọi phiên trỏ tới bill.
- `Payment` tạo ở `CashierPaymentServiceImpl:206`.
- `OrderDraft` dùng ở: `WaiterServiceImpl`, `MenuServiceImpl` (existsByMenuItemId), `OrderDraftRepository`, + snapshot (WorkspaceSnapshotsImpl) – 6 file.

### Enum dùng chung
- `Role` {CUSTOMER, ADMIN, MANAGER, RECEPTIONIST, WAITER, KITCHEN, CASHIER}; CUSTOMER chỉ còn dùng làm chặn/lọc ở 6 chỗ (AdminUserServiceImpl, ManagerEmployeeServiceImpl, ManagerIncidentServiceImpl, ManagerShiftServiceImpl, WorkspaceSnapshotsImpl, test).
- `Payment.Method` {CASH, TRANSFER} (enum lồng trong entity). `Reservation.Source` {WEB, PHONE, WALK_IN}. `User` được tham chiếu bởi ~103 file (đổi tên lớn).
- `Bill.discountAppliedBy`, `Payment.createdBy/verifiedBy` hiện là email (String).
- Auth: `/api/v1/auth/**` permitAll; public: GET menu, GET reservations/settings, POST reservations, lookup, cancel, customers/register. `CustomerController /register` (đăng ký thành viên) tồn tại – nằm ngoài phạm vi bản chốt, giữ nguyên.

### FE web – màn hình & API bị ảnh hưởng
- Admin: AdminUsersPage, AdminRolesPage (`admin/users`, role CUSTOMER), Settings/Restaurant (thêm bank_bin).
- Manager: EmployeesPage (`manager/employees`), ReservationsPage, OperationsPage, OrdersPage, ShiftsPage, ApprovalsPage, Dashboard/Reports (payments.method).
- Cashier: CashierPosPage, CashierBillPage, CashierPaymentPage (cash/transfer/confirm), CashierReceiptPage, CashierShiftPage (đóng ca), CashierTransactions*, CashierRequestsPage. API `cashier/bills/{id}/payments/transfer`, `.../confirm`.
- Customer site (sea-crown): catering/BookingForm, DepositPanel, ReservationStatusPage, MemberSignup. Chưa có `/payment/result`.
- Lễ tân và phục vụ nằm ở **mobile** (Expo): reception/*, waiter/* (cart, payment, tables/[id]). Master prompt chỉ nhắc FE web; mobile gọi cùng API `workspaces/staff` nên sẽ bị ảnh hưởng → xem câu hỏi bên dưới.
- Phục vụ gửi lệnh qua `workspaces/staff/commands` (add-draft, submit...) và `/waiter/sessions/{id}/drafts`.

### Ảnh hưởng khác
- `dataset.sql` 403 dòng dùng tên bảng `users`, `order_drafts`, `bills.session_id`… → phải viết lại cho schema mới (Giai đoạn 4).
- Test: 28 file test tích hợp (Testcontainers), nhiều file dùng `User`, `bills.session`; cần cập nhật cùng đợt.
- Docker: Postgres 17 chạy ở cổng 5432 (container `restosphere-postgres-1`); đã tạo DB trống `restosphere_v2` để chạy Flyway/test. Port 8080 đang bị chiếm bởi tiến trình khác (Java) → khi chạy thử dùng `SERVER_PORT=8090`.
- `application.yaml` có thay đổi cục bộ chưa commit của người dùng (`SQL_INIT_MODE` mặc định `always`). Không đụng vào cho tới khi cần sửa file này (khi sửa sẽ giữ nguyên dòng đó).

## Nhật ký
- 2026-11-23 19:06 – GĐ1: Flyway dep + rename User->Employee xong, 196/196 test pass. Baseline trước đó fail toàn bộ vì Flyway bật mà DB không rỗng -> đặt flyway.enabled=false tạm.
- 2026-11-23 18:50 – Tạo nhánh refactor/db-flyway-payos từ develop. Lưu ý: `application.yaml` có thay đổi chưa commit của người dùng (SQL_INIT_MODE mặc định never -> always), giữ nguyên, không đưa vào commit của mình cho tới khi cần.



