# RestoSphere Refactor – Tiến độ

## Trạng thái
- Nhánh: refactor/db-flyway-payos (BE: restosphere-be-springboot; FE web: restophere-fe-web, nhánh develop)
- Giai đoạn hiện tại: 2 (sau khi xử lý xong 8 mục phản hồi báo cáo GĐ1)
- Cập nhật lần cuối: 2026-11-24

## Đang làm
- GĐ2 bắt đầu: cấu hình SDK payOS (PayosProperties + PayosGateway + bean, key đọc từ env) cùng pom.xml (payos-java 2.0.1, đã xác nhận là dependency của đợt refactor, commit cùng bước này). Chưa viết dòng code GĐ2 nào.

## Câu hỏi đang chờ
- [x] FE mobile / màn lễ tân + phục vụ -> đã trả lời, xem mục Quyết định
- [x] areas `active` + `sort_order` -> đã trả lời
- [x] `reservations.area_id` NULL -> đã trả lời
- [x] Seed dev: dataset.sql sang Flyway -> đã trả lời
- [ ] `BillingWorkflowImpl.closeSessions` (TODO(confirm)): thanh toán xong bill thì phiên đóng + bàn về AVAILABLE ngay, không còn bước `guest-left`/`clean` của phục vụ. Endpoint `guest-left`/`clean` còn trong code nhưng không còn dùng được sau khi bill PAID (phiên đã CLOSED). Phương án an toàn đã chọn; mobile `finish-screen` cần sửa ở GĐ3. (2026-11-24)
- [ ] Đặt bàn nhiều bàn: số khách chia cho các bàn theo sức chứa, mỗi bàn tối thiểu 1; lễ tân/quản lý nhận `tableIds` (mảng) hoặc `tableId`. Đổi bàn khi đã SEATED chỉ cho đặt bàn 1 bàn. TODO(confirm) cách chia khách. (2026-11-24)


## Quyết định đã nhận từ người dùng trong lúc làm
- 2026-11-24 – `orders.submitted_at`: ĐỒNG Ý giữ (bếp cần giờ gửi thật); đã bỏ TODO(confirm) trong `Order.java`.
- 2026-11-24 – Phản hồi báo cáo GĐ1 (8 mục, xử lý trước payOS): (1) thử sai xác nhận CK thủ công như login + audit_logs mọi lần; (2) tách bàn bắt buộc `guestCount`; (3) WAITER chỉ ghép/tách phiên mình phụ trách; (4) nhiều phiên cho reservation nhiều bàn + bill PAID đóng mọi phiên; (5) seed đổi `V1_1__dev_seed.sql`, MANUAL_TRANSFER phải COMPLETED + verified_by_id; (6) DB dev mặc định `restosphere_v2`; (7) giữ `submitted_at`; (8) tắt `spring.sql.init`.
- 2026-11-24 – Xung đột AGENTS.md vs master prompt -> trong đợt refactor (nhánh refactor/db-flyway-payos) master prompt được ưu tiên ở 2 điểm: (1) được tạo/sửa Flyway migration (V1, seed); (2) chỗ chưa rõ KHÔNG dừng hỏi: ghi câu hỏi + chọn phương án an toàn + TODO(confirm) rồi làm tiếp. Mọi quy tắc khác của AGENTS.md giữ nguyên. Việc thêm: ở GĐ4 cập nhật AGENTS.md (bỏ cấm migration, thay bằng quy tắc Flyway: mỗi thay đổi schema = 1 file migration mới, không sửa migration đã chạy, không trùng version; giữ quy tắc hỏi khi chưa rõ cho việc thường ngày sau đợt này). pom.xml chưa commit: là dependency payos-java 2.0.1 của đợt refactor -> commit cùng tính năng cấu hình SDK.
- 2026-11-23 – Git: cứ 2 commit thì push 1 lần (`git push -u origin refactor/db-flyway-payos`, không force). Lần push đầu đẩy toàn bộ commit tồn đọng.
- 2026-11-23 – FE mobile: KHÔNG tạo trang lễ tân/phục vụ trên web. Mọi tính năng LỄ TÂN + PHỤC VỤ làm trên MOBILE (restosphere-fe-mobile): giỏ DRAFT -> gửi bếp; tạo đặt bàn quầy/hotline; "Thu cọc" QR payOS tự cập nhật; "Copy link thanh toán"; "Chuyển thu ngân thu cọc tiền mặt"; tách/ghép bàn; "Kiểm tra lại". FE web chỉ: quản lý (nhân viên, cài đặt, xác nhận CK thủ công), thu ngân (3 phương thức, in bill QR, "Cọc chờ thu", đóng ca), đặt bàn online public + /payment/result. Mobile là BẮT BUỘC của GĐ3 (làm web xong thì làm mobile). Skill FE chủ yếu cho web; mobile giữ style/component sẵn có.
- 2026-11-23 – areas: THÊM `active` (boolean NOT NULL DEFAULT true) và `sort_order` (int NOT NULL DEFAULT 0). Khu active=false: không hiện khi đặt online, không cho xếp bàn mới, dữ liệu cũ giữ. Sắp xếp theo sort_order rồi name. Màn quản lý khu (web): thêm bật/tắt + sửa thứ tự.
- 2026-11-23 – reservations.area_id cho phép NULL: form có "Không yêu cầu khu"; lễ tân tự xếp bàn khu còn chỗ; sửa index/query/kiểm tra chỗ trống xử lý area_id NULL.
- 2026-11-23 – Seed: chuyển dataset.sql sang Flyway thư mục riêng (classpath:db/seed), CHỈ profile dev (thêm location vào spring.flyway.locations của profile dev), chạy sau migration, nạp 1 lần. Tắt spring.sql.init. Xóa dataset.sql sau khi chuyển.
- 2026-11-23 – Nếu V1 đã tạo/commit thì SỬA THẲNG V1 cho thay đổi schema (chưa ai chạy V1), rồi drop + tạo lại restosphere_v2 để kiểm tra; ghi vào Nhật ký.

## Danh sách công việc
### Phản hồi báo cáo GĐ1 (làm trước GĐ2)
- [x] 1 Xác nhận CK thủ công: giới hạn thử sai + audit — commit `8c46c13` (`FailureLimiter` dùng chung với login; 10 lần sai/15 phút theo email quản lý -> 429; audit `manual-transfer-denied/-blocked/-confirmed` ghi bằng `AuditRecorder` transaction riêng nên không mất khi rollback)
- [x] 2 Tách bàn bắt buộc số khách — commit `9b242d4` (`guestCount` 1 <= n < số khách hiện tại; trừ ở bàn cũ; bỏ TODO(confirm); API `split` đổi: thêm field bắt buộc `guestCount`, FE phải cập nhật ở GĐ3)
- [x] 3 Quyền ghép/tách: WAITER chỉ phiên mình — commit `5fe0843`
- [x] 4 Nhiều phiên cho reservation nhiều bàn + đóng mọi phiên khi PAID — commit `09c43b6` (`MultiTableReservationTests` 4 test; `ReservationWorkflow.assign(r, List)`; `handover` mở mỗi bàn 1 phiên + 1 bill, cọc gắn bill phiên đầu; `settle` đóng mọi phiên + trả bàn + COMPLETED reservation khi hết phiên mở; void thanh toán mở lại phiên nếu bàn chưa bị lấy)
- [x] 5 Seed `V1_1__dev_seed.sql` — commit `c489bb4` (CK thủ công luôn COMPLETED + verified_by manager + mã GD; payment chưa xác nhận là PAYOS; thêm kiểm tra vào `RestosphereBeSpringbootApplicationTests`)
- [x] 6 DB dev mặc định `restosphere_v2` — commit `5bdee77` (application.yaml, .env.example, compose.yaml, README; `JPA_DDL_AUTO=validate`)
- [x] 7 `orders.submitted_at` giữ — commit `88d5501`
- [x] 8 Tắt `spring.sql.init`, bỏ `SQL_INIT_MODE` — commit `ea3cbca`
- Kết quả test sau 8 mục: `./mvnw -o test` 211/211 pass (Testcontainers qua Flyway).

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
- [x] Gộp order_drafts vào orders — commit `fd70c7e` (thêm orders.status/submitted_at, order_items.DRAFT, `OrderItem.NOT_BILLABLE`, partial unique index 1 DRAFT/(session,waiter) trong DatabaseConstraintsInitializer tạm thời; endpoint giữ nguyên)
- [x] Phiên-bill A1: table_sessions.bill_id, bỏ bills.session_id, bill tạo khi mở phiên — commit `ca4f0d7` (Bill có `sessions`; `bill.tableCodes()`/`guestCount()`/`awaitingPayment()`; bỏ unique reservation_id; response bill: `tableCode` có thể là "T1 + T2")
- [x] Phiên-bill A2: ghép/tách + luật chặn — commit `ed7c60c` (API mới: `POST /api/v1/table-sessions/{id}/merge` body `{sourceSessionId}`, `POST /api/v1/table-sessions/{id}/split` body `{targetTableId, itemIds[]}`; quyền ADMIN/MANAGER/RECEPTIONIST/WAITER/CASHIER; chặn: giảm giá/đang chờ duyệt giảm giá, có payment COMPLETED hoặc PENDING (payOS), bàn có cọc đã trả, phiên đã yêu cầu thanh toán; mutation-test xác nhận test chặn thật sự bắt luật). TODO(confirm): tách bàn đặt số khách bàn mới = 1
- [x] Payments: cột mới, method mới, cọc, MANUAL_TRANSFER, chặn đóng ca — commit `34f4c18` (Payment.bill/cashier_shift nullable + reservation_id, payos_order_code, checkout_url, proof_image_url; method CASH/PAYOS/MANUAL_TRANSFER; cọc tra qua payments, bỏ reservations.deposit_paid_at khỏi logic (cột còn trong entity, bỏ ở V1); bỏ lệnh confirm-deposit; không hoàn cọc; cọc tự gắn bill khi handover; endpoint `/payments/transfer`, `/payments/{id}/confirm|cancel` XÓA, thay bằng `POST /cashier/bills/{id}/payments/manual-transfer` (quản lý nhập email+mật khẩu); chặn đóng ca khi còn PENDING giữ nguyên). Còn thiếu: nút/DTO `bank_bin`, giá trị CHECK tiền cọc cho method PAYOS ở GĐ2
- [x] areas: active + sort_order — commit `fe0fad2` (BE xong; UI quản lý khu ở GĐ3 web). API `/areas` trả thêm `active`, `sortOrder`; `AreaRequest` nhận `active`/`sortOrder` tùy chọn; public settings chỉ trả khu active
- [x] V1 migration + validate — commit `10d7816` (`db/migration/V1__init_schema.sql`: 24 bảng, tên `pk_/uq_/fk_/ck_/idx_`, 3 partial unique index; `DatabaseConstraintsInitializer` đã xóa; `ddl-auto=validate`, `flyway.enabled=true`). Đã chạy app thật trên DB trống `restosphere_v2`: Flyway áp V1 + validate pass; so với schema Hibernate sinh: cột/ràng buộc/index trùng 100% (chỉ khác tên). Còn lại: chuyển seed `dataset.sql` -> `db/seed` (profile dev), bỏ `reservations.deposit_paid_at` (đã không có trong V1)
### Giai đoạn 2
- [ ] Cấu hình SDK payOS
- [ ] Link payOS cho bill + hủy link
- [ ] Link cọc (online, hotline, quầy)
- [ ] Cọc tiền mặt / MANUAL_TRANSFER (Cọc chờ thu)
- [ ] Kiểm tra lại + job hết hạn cọc
- [ ] Webhook + endpoint dev
- [ ] Endpoint tra trạng thái cho FE
### Giai đoạn 3
- [ ] FE web: quản lý (nhân viên, cài đặt + bank_bin + preview QR, khu active/sort, xác nhận CK thủ công)
- [ ] FE web: thu ngân (3 phương thức, in bill QR, Cọc chờ thu, biên nhận cọc, đóng ca bị chặn, Kiểm tra lại)
- [ ] FE web: đặt bàn online (chính sách không hoàn cọc, payOS, /payment/result, hotline khi lỗi, ẩn khu inactive, "Không yêu cầu khu")
- [ ] FE MOBILE (BẮT BUỘC): phục vụ (giỏ DRAFT -> gửi bếp, tách/ghép bàn)
- [ ] FE MOBILE (BẮT BUỘC): lễ tân (đặt bàn quầy/hotline, Thu cọc QR, Copy link, chuyển thu ngân, Kiểm tra lại)
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
- 2026-11-23 21:59 – GĐ1: V1 xong và chạy thật trên restosphere_v2. Phát hiện: `spring.jpa.defer-datasource-initialization: true` làm Hibernate validate chạy TRƯỚC Flyway -> đã bỏ. Test `DatabaseConstraintsTests`/`RestosphereBeSpringbootApplicationTests` không còn `create-drop`, giờ chạy qua Flyway thật (202/202 pass). DB tạm restosphere_gen đã xóa; DB `restosphere` cũ KHÔNG đụng tới.
- 2026-11-23 21:13 – GĐ1: ghép/tách xong, 201/201 test pass. Tách bàn: bill cũ được tính lại, bill mới chứa món chuyển; ghép: bill của phiên nguồn -> VOIDED và bị ẩn khỏi danh sách.
- 2026-11-23 20:58 – GĐ1: payments xong, 194/194 test pass (bỏ CashierTransferApiIntegrationTests, thêm CashierManualTransferApiIntegrationTests; sửa test cọc lễ tân theo payments). Bắt đầu push mỗi 2 commit.
- 2026-11-23 20:21 – GĐ1: A1 phiên-bill xong, 197/197 test pass. dataset.sql tạm sửa (bill chèn trước phiên, cập nhật tổng sau). Nhánh CHƯA push (người dùng hỏi; chờ xác nhận).
- 2026-11-23 19:55 – GĐ1: areas active/sort_order + area_id NULL xong, 197/197 test pass (thêm 1 test mới). Lưu ý: `reservations.areaName` giờ là "Không yêu cầu khu" khi NULL.
- 2026-11-23 19:37 – GĐ1: gộp order_drafts vào orders xong, 196/196 test pass. Đã nhận 4 quyết định từ người dùng (mobile bắt buộc, areas active/sort_order, area_id NULL, seed -> Flyway db/seed). Sẽ sửa thẳng V1 khi tạo, drop/tạo lại restosphere_v2 để kiểm tra.
- 2026-11-23 19:06 – GĐ1: Flyway dep + rename User->Employee xong, 196/196 test pass. Baseline trước đó fail toàn bộ vì Flyway bật mà DB không rỗng -> đặt flyway.enabled=false tạm.
- 2026-11-23 18:50 – Tạo nhánh refactor/db-flyway-payos từ develop. Lưu ý: `application.yaml` có thay đổi chưa commit của người dùng (SQL_INIT_MODE mặc định never -> always), giữ nguyên, không đưa vào commit của mình cho tới khi cần.










