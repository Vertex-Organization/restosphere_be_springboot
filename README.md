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
| `DB_NAME` | `restosphere` | Tên database |
| `DB_USERNAME` | `restosphere` | User database |
| `DB_PASSWORD` | `restosphere` | Mật khẩu database, chỉ dùng mặc định khi dev |
| `JPA_DDL_AUTO` | `update` | Chế độ `ddl-auto` của Hibernate, production nên dùng `validate` |

Tài khoản admin chỉ được tạo khi email chưa tồn tại, nên khởi động lại app không ghi đè mật khẩu đã đổi.

### Tài khoản demo web và mobile

Sau khi Hibernate tạo schema, chạy `src/main/resources/dataset.sql` trên database local (file này không tự chạy khi khởi động).
Ví dụ với PostgreSQL CLI: `psql -h localhost -U restosphere -d restosphere -v ON_ERROR_STOP=1 -f src/main/resources/dataset.sql`.

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

Vai trò người dùng gồm `ADMIN`, `MANAGER` và `STAFF`.

## Test

```bash
./mvnw test
```

Cần Docker đang chạy.

## Cấu trúc thư mục

```text
src/main/java/com/vertex/restosphere
├── common       # Enum và tiện ích dùng chung
├── config       # Cấu hình security, JWT, OpenAPI, tạo admin đầu tiên
├── controller   # REST controller
├── dto          # Request và response
├── entity       # JPA entity
├── exception    # Exception và xử lý lỗi tập trung
├── mapper       # Chuyển đổi entity sang DTO
├── repository   # Spring Data repository
├── security     # Phát hành token và nạp thông tin người dùng
└── service      # Nghiệp vụ
```
