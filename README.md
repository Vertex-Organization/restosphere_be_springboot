# RestoSphere Backend

Backend Spring Boot của RestoSphere. Hiện tại project đã có phần xác thực người dùng bằng JWT, gồm access token và refresh token.

## Công nghệ

- Java 21, Spring Boot 4.1.2
- Spring Security, OAuth2 Resource Server (JWT)
- Spring Data JPA, PostgreSQL 17
- springdoc-openapi (Swagger UI)
- Lombok, Bean Validation
- JUnit, Testcontainers

## Yêu cầu

- JDK 21
- Docker (app tự khởi động PostgreSQL bằng `compose.yaml`, test cũng cần Docker cho Testcontainers)

Không cần cài Maven, project đã có sẵn Maven Wrapper.

## Chạy local

1. Tạo file `.env` từ mẫu:

   ```bash
   cp .env.example .env
   ```

2. Điền các giá trị trong `.env`:
   - `JWT_SECRET`: bắt buộc, tối thiểu 32 ký tự. Có thể tạo bằng `openssl rand -hex 32`.
   - `ADMIN_EMAIL` và `ADMIN_PASSWORD`: tài khoản admin đầu tiên. Project chưa có API đăng ký, nên cần hai biến này để có tài khoản đăng nhập.

3. Chạy app:

   ```bash
   ./mvnw spring-boot:run
   ```

   Trên Windows dùng `mvnw.cmd spring-boot:run`.

App chạy ở `http://localhost:8080`. PostgreSQL được khởi động cùng app và vẫn chạy sau khi app tắt.

## Biến môi trường

| Biến | Mặc định | Mô tả |
| --- | --- | --- |
| `JWT_SECRET` | (không có) | Khóa ký JWT, bắt buộc, tối thiểu 32 ký tự |
| `JWT_ISSUER` | `restosphere` | Issuer của JWT |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | Thời hạn access token |
| `JWT_REFRESH_TOKEN_TTL` | `7d` | Thời hạn refresh token |
| `ADMIN_EMAIL` | (trống) | Email admin đầu tiên |
| `ADMIN_PASSWORD` | (trống) | Mật khẩu admin đầu tiên |
| `DB_HOST` | `localhost` | Host PostgreSQL |
| `DB_PORT` | `5432` | Cổng PostgreSQL |
| `DB_NAME` | `restosphere_v2` | Tên database (đợt refactor Flyway dùng DB mới, không dùng `restosphere` cũ) |
| `DB_USERNAME` | `restosphere` | User database |
| `DB_PASSWORD` | `restosphere` | Mật khẩu database, chỉ dùng mặc định khi dev |
| `JPA_DDL_AUTO` | `validate` | Chế độ `ddl-auto` của Hibernate; schema do Flyway quản lý nên giữ `validate` |
| `SWAGGER_ENABLED` | `false` | `true` để bật Swagger UI và `/v3/api-docs` |
| `FORWARD_HEADERS_STRATEGY` | `none` | Đặt `framework` khi chạy sau reverse proxy để giới hạn tốc độ nhận đúng IP khách |

Tài khoản admin chỉ được tạo khi email chưa tồn tại, nên khởi động lại app không ghi đè mật khẩu đã đổi.

### Tài khoản demo web và mobile

Dữ liệu demo nằm ở `src/main/resources/db/seed/V1_1__dev_seed.sql`, chỉ được nạp (một lần, sau migration schema) khi chạy với `SPRING_PROFILES_ACTIVE=dev`.

Mật khẩu chung: `123456`, lưu dưới dạng BCrypt. Chỉ dùng dữ liệu này cho môi trường phát triển.

| Ứng dụng | Email | Vai trò |
| --- | --- | --- |
| Web | `admin@restosphere.test` | ADMIN |
| Web | `manager@restosphere.test` | MANAGER |
| Web | `cashier@restosphere.test` | CASHIER |
| Web | `kitchen@restosphere.test` | KITCHEN |
| Mobile | `receptionist@restosphere.test` | RECEPTIONIST |
| Mobile | `waiter@restosphere.test` | WAITER |

Màn hình đăng nhập web/mobile ở chế độ development có nút tự điền các tài khoản tương ứng.
Chạy lại SQL không ghi đè tài khoản đã tồn tại, bao gồm mật khẩu.

## API

Swagger UI: <http://localhost:8080/swagger-ui.html>

OpenAPI JSON: <http://localhost:8080/v3/api-docs>

| Method | Đường dẫn | Xác thực | Mô tả |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | Không | Đăng nhập, trả về access token và refresh token |
| `POST` | `/api/v1/auth/refresh` | Không | Đổi refresh token lấy cặp token mới |
| `POST` | `/api/v1/auth/logout` | Không | Thu hồi refresh token |
| `GET` | `/api/v1/auth/me` | Bearer | Lấy thông tin người dùng hiện tại |
| `GET` | `/actuator/health` | Không | Kiểm tra trạng thái app |

Các API có xác thực cần header `Authorization: Bearer <accessToken>`.

Mỗi refresh token chỉ dùng được một lần. Nếu một token đã thu hồi bị gửi lại, toàn bộ chuỗi token cùng phiên đăng nhập sẽ bị thu hồi. Logout cũng thu hồi cả chuỗi này.

Vai trò nhân viên gồm `ADMIN`, `MANAGER`, `RECEPTIONIST`, `WAITER`, `KITCHEN`, `CASHIER`. Khách đặt bàn công khai, không có role CUSTOMER.

## Flyway và cập nhật database nhóm

- Schema mới gồm 24 bảng, khởi tạo bởi `src/main/resources/db/migration/V1__init_schema.sql`; Hibernate chỉ `validate`.
- Mỗi thay đổi schema tạo migration mới. Không sửa migration/seed đã chạy, không sửa checksum hoặc dùng repair để che thay đổi.
- Version phải duy nhất: thống nhất với nhóm hoặc dùng timestamp; kiểm tra không trùng trước merge.
- Khi chuyển từ schema cũ, nhóm cần một lần khởi tạo DB phát triển sạch. Ưu tiên database mới `restosphere_v2`, giữ DB cũ `restosphere` nguyên vẹn. Chỉ chủ sở hữu được quyết định xóa DB local sau khi sao lưu; không áp dụng thao tác này cho production.
- Sau lần chuyển đổi, chỉ pull và chạy app để Flyway nâng version; không drop lại mỗi lần chạy.
- Seed chỉ chạy với profile `dev`, một lần mỗi version. `spring.sql.init` tắt. Không bật profile dev ở production.
- Test dùng PostgreSQL Testcontainers, không trỏ vào DB local.

## payOS: cấu hình và thử local

| Biến | Ý nghĩa |
| --- | --- |
| `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` | Khóa server; mặc định `not-configured`, không gửi xuống FE hoặc commit |
| `PAYOS_FAKE` | `true` mặc định trong profile dev, `false` ngoài dev |
| `PAYOS_RETURN_URL` | Mặc định `http://localhost:5173/payment/result` |
| `PAYOS_CANCEL_URL` | Mặc định `http://localhost:5173/payment/result?cancelled=true` |
| `PAYOS_BILL_LINK_TTL` | Hạn link bill, mặc định `30m` |
| `PAYOS_TIMEOUT` | Timeout gateway, mặc định `10s` |

Thử không dùng tiền thật: chạy profile dev với `PAYOS_FAKE=true` trên DB phát triển riêng, tạo link qua luồng bill/cọc, lấy `orderCode` từ response rồi gọi:

```bash
curl -X POST http://localhost:8080/api/dev/payos/fake-paid/ORDER_CODE
```

Thay `ORDER_CODE` bằng mã của payment vừa tạo. Endpoint chỉ tồn tại ở profile dev, từ chối nếu gateway không phải fake; dùng chung xử lý paid với webhook. Không công khai môi trường dev qua Internet.

Kiểm thử webhook thật bằng ngrok chỉ khi người phụ trách chủ động cho phép: dùng môi trường thử tách biệt, không bật endpoint dev, cấu hình khóa ở môi trường server, chạy `ngrok http 8080` và đăng ký URL HTTPS ngrok nối `/api/payments/payos-webhook` trong payOS. Không coi hướng dẫn này là kết quả đã kiểm thử; đợt refactor không gọi payOS thật. Chữ ký được xác minh bằng SDK v2; chữ ký sai trả 400, mã lạ trả 200, callback lặp không thu hai lần.

### Luồng payOS dùng khi trình bày đồ án

1. Server tính số tiền từ bill hoặc đặt bàn, sinh `payos_order_code` duy nhất và lưu payment PENDING cùng link/QR.
2. Khách quét QR và trả vào tài khoản chính thức của nhà hàng; FE không tự đánh dấu đã trả từ query string returnUrl.
3. payOS gửi webhook; server xác minh chữ ký, so số tiền và xử lý idempotent. Nút **Kiểm tra lại** hỏi gateway rồi dùng chung logic cập nhật.
4. Cọc COMPLETED xác nhận đặt bàn, khi check-in gắn vào bill đầu tiên. Bill đủ tiền chuyển PAID, đóng mọi phiên liên quan và trả bàn.
5. Bill đổi tiền thì hủy link cũ; thu ngân tạo/in link mới. Ca còn payment payOS PENDING không được đóng.
6. Hết hạn cọc: hỏi gateway trước khi hủy. Tiền đến sau hủy không khôi phục đặt bàn, báo quản lý xử lý. Không hoàn cọc.

Quy trình khi hệ thống hoặc payOS lỗi: [QUY_TRINH_SU_CO.md](docs/QUY_TRINH_SU_CO.md). Tiến độ FE và các kiểm thử còn thiếu: [REFACTOR_PROGRESS.md](docs/REFACTOR_PROGRESS.md).

## Test

```bash
./mvnw test
```

Cần Docker đang chạy.

## Cấu trúc thư mục

```text
src/main/java/com/vertex/restosphere
├── common       # Enum, tiện ích dùng chung (util/Values: đọc và kiểm tra payload)
├── config       # Cấu hình security, JWT, OpenAPI, tạo admin đầu tiên
├── controller   # REST controller
├── dto          # Request và response
├── entity       # JPA entity
├── exception    # Exception và xử lý lỗi tập trung
├── mapper       # Chuyển entity sang Map/DTO cho workspace và snapshot
├── repository   # Spring Data repository
├── security     # Phát hành token và nạp thông tin người dùng
└── service      # Nghiệp vụ: interface ở service/, cài đặt ở service/impl/
```
