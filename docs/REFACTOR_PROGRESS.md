# RestoSphere Refactor – Tiến độ

## Trạng thái
- Nhánh: `refactor/db-flyway-payos` ở cả 3 repo (BE `restosphere-be-springboot`, web `restophere-fe-web`, mobile `restosphere-fe-mobile`), đã push lên GitHub.
- Cập nhật lần cuối: 2026-11-24. Đợt A–E đã làm xong ở mức code + test tự động; CHƯA kiểm thử tay trên máy chạy thật (xem "Kết quả cuối").

## Kết quả cuối

### Đã chạy thật (không chỉ phân tích)
- BE: `./mvnw -o spotless:check` sạch, `./mvnw -o test` **256/256 pass** (Testcontainers, Flyway thật).
- Web: `npm run lint` sạch, `npm run build` pass, 9 file `node --test scripts/*.test.mjs` đều exit 0.
- Mobile: `npm test` **83/83 pass**, `npm run typecheck` và `npm run lint` sạch.
- KHÔNG làm: kiểm thử giao diện trên trình duyệt/thiết bị thật, gọi payOS thật (chưa có key), chạy trên DB `restosphere`.

### Commit theo hạng mục
| Mục | Nội dung | Commit |
|---|---|---|
| A1 | Giới hạn thử sai xác nhận CK thủ công như login + audit mọi lần (thành công, sai, bị chặn, và mỗi lần quản lý xác thực đúng) | BE `8c46c13`, `f21aaa1` |
| A2 | Tách bàn bắt buộc `guestCount`, trừ bàn cũ, mỗi bàn >= 1 | BE `9b242d4` |
| A3 | WAITER chỉ ghép/tách phiên mình; ghép kiểm tra mọi phiên của cả hai bill | BE `5fe0843`, `f21aaa1` |
| A4 | Nhiều bàn: mọi phiên dùng CHUNG 1 bill (cọc gắn bill đó), chia khách theo sức chứa, mỗi bàn >= 1; check-in mở phiên luôn, handover tương thích/idempotent; PAID đóng mọi phiên | BE `09c43b6`, `1dc2b8b`, `f21aaa1` |
| A5 | Seed `V1_1__dev_seed.sql` (db/seed, chỉ profile dev); CK thủ công luôn COMPLETED + `verified_by_id` | BE `c489bb4` |
| A6 | DB dev đọc từ biến môi trường, mặc định `restosphere_v2` | BE `5bdee77` |
| A7 | `orders.submitted_at` giữ, bỏ TODO(confirm) | BE `88d5501` |
| A8 | Tắt `spring.sql.init`, bỏ `SQL_INIT_MODE` | BE `ea3cbca` |
| B | PAID -> phiên CLOSED ngay, bàn CLEANING (mọi bàn của bill); `clean` chạy được với phiên CLOSED -> AVAILABLE; `guest-left` chỉ ghi dấu tùy chọn; lễ tân không xếp được bàn CLEANING; chỉ phiên mới nhất của bàn mới cho dọn; mobile finish-screen | BE `1c14da5`, `f21aaa1`; mobile `a8c953e` |
| C | QR vẽ cục bộ: web `qrcode.react`, mobile `react-native-qrcode-svg` + `react-native-svg 15.16.0` (qua `expo install`); gỡ hoàn toàn `api.qrserver.com`; mobile hiện QR cọc và QR hóa đơn trong app, vẫn giữ nút mở link/gửi link | web `4af3f1a`; mobile `a9ce859`, `292cfd3` |
| D | Mobile: fixture/test theo luật mới (không hoàn cọc, CLEANING, DRAFT, ghép/tách, chặn khi có cọc/QR/đã yêu cầu thanh toán); BE: `EndToEndPaymentFlowTests` gọi HTTP thật `POST /api/dev/payos/fake-paid/{orderCode}` cho 2 luồng (DRAFT -> bếp -> bill -> payOS -> PAID -> CLEANING; cọc -> check-in nhiều bàn -> bill chung có cọc) | BE `4e04849`, `f21aaa1`; mobile `292cfd3` |

Các commit trước đó của đợt: BE GĐ2 payOS (`aed3298`...`e16b051`), web `ff79893`, `e27d9ad`, `4bd423d`, `9b9f06a`, `bbd96b3`, `2dbbb5a`, mobile `5504ea4`, `fbca6d2`.

### TODO(confirm) còn lại trong code
- Đặt bàn quầy dùng `input.counter=true` (source WALK_IN, giữ PENDING) để phân biệt với `walkIn` (ngồi ngay).
- `POST /api/v1/reservations/payment-status` là endpoint công khai mới (rate limit, cần đúng mã đặt bàn + SĐT).
- Chia khách nhiều bàn theo sức chứa, bàn cuối nhận phần còn lại, mỗi bàn >= 1.
- Mobile `check-in` mở phiên ngay: màn bàn giao cũ không còn là bước bắt buộc.

### Câu hỏi đang chờ
- Hai câu hỏi trước đã được trả lời: thêm thư viện QR (đồng ý, đã làm), bàn tự trống sau thanh toán (KHÔNG; đã đổi sang CLEANING).
- Chưa có câu hỏi mới.

### Việc CHƯA làm / hạn chế đã biết
- Chưa thử payOS thật; webhook thật chỉ kiểm bằng chữ ký SDK + bản giả. Cần key payOS và URL công khai (ngrok) để thử.
- Chưa kiểm thử giao diện: màn thu ngân, Cọc chờ thu, đặt bàn online, `/payment/result`, và toàn bộ màn mobile chỉ qua type-check/lint/test logic.
- Fixture mobile là bản mô phỏng của server; test mobile không chạm BE thật. Tách/ghép/nháp trên mobile chưa chạy với BE thật.
- Web: ảnh QR tĩnh VietQR vẫn lấy từ `img.vietqr.io` (dịch vụ ảnh công khai, không phải QR thanh toán payOS).
- Mobile: `expo-doctor` báo thiếu peer `expo-font` (có từ trước, không do đợt này).
- Bộ đếm thử sai nằm trong bộ nhớ (1 instance); `/payment/result` nhớ đơn trong sessionStorage (thiết bị khác phải tra cứu bằng mã + SĐT).
- Remote web báo repo đã đổi sang `Vertex-Organization/restosphere_fe_web`; vẫn push được qua origin cũ.
- Ghi chú git: BE đã push `c803cd4`, `7533a56` trước khi có quyền push, đã báo; chưa sửa lịch sử.

### Hướng dẫn tự test thủ công
1. DB: tạo DB trống `restosphere_v2` (Postgres, user/mật khẩu như `.env.example`). KHÔNG dùng DB `restosphere`.
2. BE: copy `.env.example` thành `.env`, điền `JWT_SECRET` (>= 32 ký tự), `ADMIN_EMAIL`, `ADMIN_PASSWORD`; để trống `PAYOS_*`. Chạy `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run` (dev bật payOS giả và nạp seed `V1_1`, mật khẩu demo `123456`).
3. Web: `npm install && npm run dev` (trong `restophere-fe-web`), mở `/vi/catering` để đặt bàn có món (tick đồng ý không hoàn cọc), `/staff/login` cho thu ngân/quản lý.
4. Mobile: `npm install && npx expo start` (trong `restosphere-fe-mobile`), đăng nhập lễ tân/phục vụ (`receptionist@restosphere.test`, `waiter@restosphere.test`).
5. Luồng nên thử: (a) đặt bàn online có món -> lấy `orderCode` -> `curl -X POST http://localhost:8080/api/dev/payos/fake-paid/<orderCode>` -> đặt bàn tự CONFIRMED; (b) lễ tân tạo đặt bàn nhiều bàn -> check-in -> kiểm tra các phiên dùng chung 1 hóa đơn và cọc nằm trên đó; (c) phục vụ gọi món (giỏ nháp -> gửi bếp) -> bếp làm -> phục vụ yêu cầu thanh toán -> thu ngân tạo QR payOS -> `fake-paid` -> bill PAID, mọi bàn CLEANING -> phục vụ bấm "Đã dọn"; (d) thử xếp khách vào bàn CLEANING (phải bị từ chối); (e) thử tách bàn thiếu/thừa số khách, ghép/tách bàn có cọc hoặc đã có QR (phải bị chặn), CK thủ công nhập sai mật khẩu quản lý 10 lần (bị khóa 15 phút, có audit).

## Câu hỏi đang chờ (lịch sử)
- [x] FE mobile / màn lễ tân + phục vụ -> đã trả lời, xem mục Quyết định
- [x] areas `active` + `sort_order` -> đã trả lời
- [x] `reservations.area_id` NULL -> đã trả lời
- [x] Seed dev: dataset.sql sang Flyway -> đã trả lời
- [x] Bàn tự trống sau thanh toán -> KHÔNG; đã chuyển sang CLEANING + "Đã dọn" (mục B)
- [x] Đặt bàn nhiều bàn dùng chung 1 hóa đơn, check-in mở phiên -> đã trả lời và làm (A4)
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
- [x] Cấu hình SDK payOS — commit `aed3298` (`PayosGateway` + `PayosGatewayImpl` SDK v2 + `FakePayosGateway`; key từ env `PAYOS_*`, mặc định giả `not-configured`; thiếu key -> 503; profile dev mặc định `PAYOS_FAKE=true`; thêm `payments.qr_code` và `reservations.deposit_cash_requested_at` vào V1; `.env.example` có mục PAYOS)
- [x] Link payOS cho bill + hủy link
- [x] Link cọc (online, hotline, quầy) — commit `4749e07` (online public: payOS lỗi -> 503 + hotline, không để lại đặt bàn; hotline: tạo link ngay, lễ tân copy `deposit.payos.checkoutUrl`; quầy: tạo đặt bàn với `input.counter=true` rồi lệnh `deposit-link` hiện QR (`deposit.payos.qrCode`), bấm lại dùng lại link cũ; cọc online xong tự CONFIRMED + tự xếp bàn; `toPublicMap` bỏ `deposit.bank` thay bằng `deposit.payos`; staff booking vẫn lưu nếu payOS lỗi). TODO(confirm): đặt bàn quầy dùng `counter` (source WALK_IN, PENDING), còn `walkIn` là ngồi ngay
- [x] Cọc tiền mặt / MANUAL_TRANSFER (Cọc chờ thu) — commit `e8e3c3b` (lệnh `deposit-cash` của lễ tân hủy link payOS + đặt `deposit_cash_requested_at`; thu ngân: `GET /cashier/deposits/pending`, `POST /cashier/reservations/{id}/deposit/cash` và `/deposit/manual-transfer`; số tiền cọc lấy từ đặt bàn, không nhận từ client; payment `reservation_id`, `bill_id` NULL, gắn ca; CK thủ công dùng chung giới hạn thử sai + audit; cọc xong tự CONFIRMED)
- [x] Kiểm tra lại + job hết hạn cọc — commit `e16b051` (`POST /api/v1/payments/{id}/refresh` cho ADMIN/MANAGER/CASHIER/RECEPTIONIST/WAITER, dùng chung `PayosPayments.refresh` với webhook; job `expireNoShows` hỏi payOS trước: đã trả -> CONFIRMED, chưa trả -> hủy đặt bàn + hủy link payOS, payOS không gọi được -> giữ lại chạy lần sau; tiền về sau khi hủy -> `LATE_PAYMENT` thông báo MANAGER, không khôi phục)
- [x] Webhook + endpoint dev — commit `73624cf` (`POST /api/payments/payos-webhook` permitAll, xác thực chữ ký, sai -> 400; `POST /api/dev/payos/fake-paid/{orderCode}` chỉ `@Profile("dev")`, gọi chung `PayosPayments.markPaid`; idempotent, khớp số tiền, mã lạ -> 200; tiền về link đã hủy -> thông báo MANAGER, không khôi phục)
- [x] Endpoint tra trạng thái cho FE — commit `e16b051` (`POST /api/v1/reservations/payment-status` công khai, body `{orderCode, code, phone}`, trả `{status, paid, reservationStatus}`; chỉ đúng mã đặt bàn + SĐT mới đọc được; hỏi payOS nếu còn PENDING; có rate limit; TODO(confirm) endpoint công khai mới)
### Giai đoạn 3
- [x] FE web: quản lý (nhân viên `ff79893`, cài đặt + bank_bin + preview QR `e27d9ad`, khu active/sort `4bd423d`; xác nhận CK thủ công nằm ở màn thu ngân vì quản lý nhập email+mật khẩu tại quầy)
- [x] FE web: thu ngân — `9b9f06a` (CASH / payOS / CK thủ công có quản lý xác nhận, in bill có QR, Kiểm tra lại, đóng ca bị chặn), `bbd96b3` (Cọc chờ thu, thu cọc tiền mặt/CK thủ công, biên nhận cọc ghi rõ không hoàn). TODO(confirm): ảnh QR payOS vẽ bằng dịch vụ ảnh công khai `api.qrserver.com` vì chưa có thư viện QR (thêm cần được duyệt); QR tĩnh dùng `img.vietqr.io` như trước. BE thêm `qrCode`, `orderCode` vào `CashierPaymentResponse` (`e05f7ed`)
- [x] FE web: đặt bàn online — `2dbbb5a` (tick đồng ý không hoàn cọc, thanh toán qua link payOS, trang `/payment/result` chỉ tin `POST /reservations/payment-status`, payOS lỗi hiện hotline do BE trả, ẩn khu inactive do BE, "Không yêu cầu khu"). Hạn chế: `/payment/result` nhớ đơn trong sessionStorage; mở trên thiết bị khác sẽ báo không thấy và gợi ý tra cứu bằng mã + SĐT
- [x] FE MOBILE (BẮT BUỘC): phục vụ — giỏ DRAFT -> gửi bếp (có sẵn), tách/ghép chặn theo quyền sở hữu/cọc/QR/đã yêu cầu thanh toán (`292cfd3`), màn kết thúc "Đã dọn" + "Khách đã về" tùy chọn (`a8c953e`). Mới kiểm bằng test logic (83/83), CHƯA chạy với BE thật hay trên thiết bị
- [x] FE MOBILE (BẮT BUỘC): lễ tân — `5504ea4` (nhiều bàn, đặt bàn quầy, mở link payOS, chuyển thu ngân, Kiểm tra lại), `fbca6d2` (gửi link Zalo/SMS), `a9ce859` (QR cọc trong app), `292cfd3` (check-in mở phiên, QR hóa đơn). CHƯA chạy trên thiết bị
### Giai đoạn 4
- [x] AGENTS.md (quy tắc Flyway: mỗi thay đổi schema một migration mới, không sửa migration đã chạy, version duy nhất; giữ quy tắc hỏi khi chưa rõ), README (Flyway, payOS, ngrok), `docs/QUY_TRINH_SU_CO.md`, tóm tắt luồng payOS trong README — `21fb95a`
- [x] Seed theo schema mới (`V1_1__dev_seed.sql`, `c489bb4`); test tích hợp xuyên suốt với endpoint fake-paid (`4e04849`, `f21aaa1`)
- [ ] Tự kiểm thử tay xuyên BE+web+mobile trên máy chạy thật: CHƯA làm, xem "Hướng dẫn tự test thủ công" ở mục Kết quả cuối

### Báo cáo Giai đoạn 2 (Backend payOS)
- Đã làm: SDK v2 + gateway (thật/giả), link bill + hủy khi bill đổi, link cọc 3 kênh, Cọc chờ thu (tiền mặt/CK thủ công), webhook + dev simulator, `Kiểm tra lại`, job hết hạn cọc hỏi payOS trước, endpoint trạng thái công khai. `./mvnw -o test` 252/252 pass.
- Commit: `aed3298` SDK, `e9492df` link bill, `73624cf` webhook, `4749e07` link cọc, `e8e3c3b` Cọc chờ thu, `e16b051` kiểm tra lại + hết hạn + trạng thái.
- Thay đổi API cho FE: `POST /cashier/bills/{id}/payos-link`; `GET /cashier/deposits/pending`; `POST /cashier/reservations/{id}/deposit/{cash|manual-transfer}`; `POST /payments/{id}/refresh`; `POST /reservations/payment-status` (công khai); lệnh workspace `deposit-link`/`deposit-cash`; `create` nhận `input.counter`; `deposit.bank` BỊ BỎ khỏi response công khai, thay bằng `deposit.payos {checkoutUrl, qrCode, orderCode, paymentId}` + `deposit.cashRequestedAt`; `/cashier/config` `methods.PAYOS` giờ bật theo cấu hình; split yêu cầu `guestCount`.
- Đổi schema (sửa thẳng V1): `payments.qr_code`, `reservations.deposit_cash_requested_at`.
- Chưa kiểm tra bằng payOS thật (chưa có key): chỉ test với `FakePayosGateway` và chữ ký webhook thật bằng crypto của SDK.
- Còn mở: sau khi thanh toán xong bàn về AVAILABLE ngay (bỏ bước guest-left/clean); mobile cần sửa `finish-screen`. Hạn chế đã biết: bộ đếm thử sai nằm trong bộ nhớ (1 instance).

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
- 2026-11-24 – Tài liệu: cập nhật AGENTS (Flyway, seed dev, vai trò; giữ quy tắc hỏi thường ngày), README (migration, biến payOS, fake-paid, ngrok, giải thích luồng), thêm QUY_TRINH_SU_CO.md. Đã chạy mvnw.cmd spotless:apply, spotless:check, -o compile, -o test; Surefire 40 lớp, không failure/error/skipped. Chưa rà soát bổ sung các edge case security/payment; test pass không thay thế review. TODO(confirm): nhập bù hồi tố/ca đã đóng chưa có API riêng, không giả dữ liệu để vượt ràng buộc; cọc CASH nhập bù dùng quản lý ký đối chiếu giấy.
- 2026-11-24 – Web quản lý khu: commit `4bd423d` thêm active/sortOrder, dialog bật/tắt + thứ tự, PUT /areas/{id}, fixture và test giữ nguyên bàn/đặt bàn khi tạm đóng. Đã chạy Prettier write, npm run lint, npm run build (tsc + Vite), node --test scripts/manager-rules.test.mjs: 30/30 PASS; git diff --check PASS. Chưa kiểm tra giao diện trình duyệt/API live. Không thêm dependency. Các tính năng thu ngân/public/mobile và rà soát BE/GĐ4 vẫn CHƯA hoàn tất; số test BE 252 và mobile 77 là lịch sử, chưa chạy lại trong phiên này.

- 2026-11-23 21:59 – GĐ1: V1 xong và chạy thật trên restosphere_v2. Phát hiện: `spring.jpa.defer-datasource-initialization: true` làm Hibernate validate chạy TRƯỚC Flyway -> đã bỏ. Test `DatabaseConstraintsTests`/`RestosphereBeSpringbootApplicationTests` không còn `create-drop`, giờ chạy qua Flyway thật (202/202 pass). DB tạm restosphere_gen đã xóa; DB `restosphere` cũ KHÔNG đụng tới.
- 2026-11-23 21:13 – GĐ1: ghép/tách xong, 201/201 test pass. Tách bàn: bill cũ được tính lại, bill mới chứa món chuyển; ghép: bill của phiên nguồn -> VOIDED và bị ẩn khỏi danh sách.
- 2026-11-23 20:58 – GĐ1: payments xong, 194/194 test pass (bỏ CashierTransferApiIntegrationTests, thêm CashierManualTransferApiIntegrationTests; sửa test cọc lễ tân theo payments). Bắt đầu push mỗi 2 commit.
- 2026-11-23 20:21 – GĐ1: A1 phiên-bill xong, 197/197 test pass. dataset.sql tạm sửa (bill chèn trước phiên, cập nhật tổng sau). Nhánh CHƯA push (người dùng hỏi; chờ xác nhận).
- 2026-11-23 19:55 – GĐ1: areas active/sort_order + area_id NULL xong, 197/197 test pass (thêm 1 test mới). Lưu ý: `reservations.areaName` giờ là "Không yêu cầu khu" khi NULL.
- 2026-11-23 19:37 – GĐ1: gộp order_drafts vào orders xong, 196/196 test pass. Đã nhận 4 quyết định từ người dùng (mobile bắt buộc, areas active/sort_order, area_id NULL, seed -> Flyway db/seed). Sẽ sửa thẳng V1 khi tạo, drop/tạo lại restosphere_v2 để kiểm tra.
- 2026-11-23 19:06 – GĐ1: Flyway dep + rename User->Employee xong, 196/196 test pass. Baseline trước đó fail toàn bộ vì Flyway bật mà DB không rỗng -> đặt flyway.enabled=false tạm.
- 2026-11-23 18:50 – Tạo nhánh refactor/db-flyway-payos từ develop. Lưu ý: `application.yaml` có thay đổi chưa commit của người dùng (SQL_INIT_MODE mặc định never -> always), giữ nguyên, không đưa vào commit của mình cho tới khi cần.










