# AGENTS.md - restosphere-be-springboot

Quy tắc bắt buộc cho mọi thành viên khi dùng agent (opencode) trên repo này. Agent phải đọc file này trước khi sửa code. Nếu yêu cầu mâu thuẫn với file này, agent nói rõ và hỏi lại, không tự bỏ qua quy tắc.

## 1. Dự án

Backend hệ thống quản lý nhà hàng RestoSphere. Java 21, Spring Boot 4.1, PostgreSQL, JPA/Hibernate, Spring Security (JWT), Lombok. Các vai trò nhân viên: `ADMIN`, `MANAGER`, `RECEPTIONIST`, `WAITER`, `KITCHEN`, `CASHIER`. Khách đặt bàn công khai, không đăng nhập.

## 2. Lệnh thường dùng

```bash
./mvnw -o compile                 # biên dịch
./mvnw -o test                    # chạy toàn bộ test (cần Docker chạy, dùng Testcontainers)
./mvnw -o test -Dtest=TênLớp      # chạy một lớp test
./mvnw spotless:apply             # format code (bắt buộc trước khi commit)
./mvnw spotless:check             # kiểm tra format
```

Chạy `spotless:apply` và `test` pass trước khi báo hoàn thành. Không bao giờ nói "xong" khi chưa chạy.

## 3. Cấu trúc thư mục

Package gốc `com.vertex.restosphere`. Mỗi loại class có một nơi duy nhất:

| Thư mục | Chứa gì | Không được chứa |
|---|---|---|
| `controller/` | REST controller: nhận request, kiểm quyền (`@PreAuthorize`), gọi service, trả response | Logic nghiệp vụ, truy cập repository |
| `service/` | Chỉ **interface** nghiệp vụ | Class cài đặt, tham chiếu tới `service/impl` |
| `service/impl/` | Cài đặt (`XxxServiceImpl`), kèm bean hỗ trợ nghiệp vụ | Annotation web (`@RequestMapping`...) |
| `repository/` | Spring Data repository | Logic nghiệp vụ |
| `entity/` | JPA entity, kế thừa `BaseEntity` | DTO, logic nghiệp vụ nặng |
| `dto/request/`, `dto/response/` | Record cho request/response | Entity |
| `mapper/` | Chuyển entity sang Map/DTO, không truy cập DB | Logic nghiệp vụ, `@Transactional` |
| `common/enums/`, `common/util/` | Enum và tiện ích dùng chung (`Values`) | Class gắn với một nghiệp vụ cụ thể |
| `config/`, `security/`, `exception/` | Cấu hình, xác thực, xử lý lỗi tập trung | Nghiệp vụ |

Quy tắc đặt file:
- Một class public một file, tên file trùng tên class.
- Dependency chỉ chạy một chiều: `controller` -> `service` (interface) -> `repository`/`mapper`. Không đảo chiều, không vòng.
- Class dùng chung nhiều nghiệp vụ vào `common/`. Chỉ một nghiệp vụ dùng thì để cạnh nghiệp vụ đó.
- Không tạo package mới khi package hiện có đã phù hợp. Muốn tạo package mới, hỏi trước.
- Script tiện ích để trong `scripts/`, không để ở thư mục gốc.

## 4. Quy tắc code sạch

- **Xóa code chết**: hàm, class, import, field, endpoint không ai gọi thì xóa, không comment lại để dành. Trước khi xóa, grep toàn repo và cả hai FE (`restophere-fe-web`, `restosphere-fe-mobile`) để chắc không ai dùng.
- **Không trùng lặp**: logic đã có thì dùng lại. Cùng một luật nghiệp vụ không được viết ở hai nơi. Tìm helper có sẵn (`common/util/Values`, `mapper/`, service hiện có) trước khi viết mới.
- **Không hardcode**: giá trị cấu hình (URL, ngưỡng, khóa) vào `application.yaml` qua biến môi trường; hằng số dùng chung vào `static final` có tên rõ.
- **Không dùng `db.all(X.class)` rồi lọc bằng stream** trong luồng xử lý request. Viết truy vấn có điều kiện (`db.query` hoặc repository) để không quét cả bảng.
- Hàm ngắn, làm một việc. Tên hàm là động từ, tên biến nói rõ ý nghĩa. Không viết nhiều câu lệnh trên một dòng.
- Không dùng `catch (Exception)` nuốt lỗi. Lỗi nghiệp vụ ném `ApiException` (`badRequest`, `notFound`, `conflict`), để `GlobalExceptionHandler` trả về.
- Không bắt `null` bằng cách bỏ qua; dùng `Optional` hoặc ném lỗi rõ ràng.
- Chỉ thêm thư viện khi thật cần và hỏi trước. Ưu tiên thư viện chuẩn và thư viện đã có trong `pom.xml`.

## 5. Format

- Java format bằng Spotless với google-java-format kiểu AOSP (thụt lề 4 dấu cách, dòng tối đa 100 ký tự). Không chỉnh tay kiểu khác, không tắt format.
- Một thay đổi format hàng loạt phải nằm trong commit riêng, không trộn với thay đổi chức năng.

## 6. Comment

- Chỉ comment để giải thích **vì sao**, khi logic không hiển nhiên. Ngắn, một dòng.
- Không comment mô tả code làm gì, không comment lịch sử sửa đổi, không để code bị comment lại.
- Dùng Javadoc ngắn cho interface/class công khai khi cần mô tả hợp đồng.

## 7. Ngôn ngữ

- Tên class, hàm, biến, commit, log bằng **tiếng Anh**.
- Thông báo trả cho người dùng cuối (message lỗi, thông báo) bằng **tiếng Việt có dấu**, không viết tiếng Việt không dấu hay telex. File nguồn lưu UTF-8.

## 8. API và dữ liệu

- Endpoint dưới `/api/v1/...`. Dùng đúng HTTP method và mã trạng thái: 400 dữ liệu sai, 401 chưa đăng nhập, 403 sai quyền, 404 không tồn tại, 409 xung đột trạng thái/phiên bản.
- Mỗi controller có `@PreAuthorize` theo vai trò. Endpoint công khai phải khai báo rõ trong `SecurityConfig`; thêm endpoint công khai cần hỏi trước.
- Kiểm tra dữ liệu đầu vào ở biên (`@Valid` trên DTO). Không tin dữ liệu từ client.
- Đổi tên field, đổi kiểu dữ liệu hoặc xóa field trong response là thay đổi phá vỡ FE. Phải báo trong mô tả PR và cập nhật cả hai FE (`restophere-fe-web`, `restosphere-fe-mobile`) trong cùng đợt.
- Thao tác ghi nhiều bước phải nằm trong một transaction. Thao tác cần tuần tự hóa dùng `RestaurantLock`, không tự tạo khóa mới.
- Tiền là số nguyên VND (`BigDecimal` scale 0), làm tròn bằng `Values.whole`. Không dùng `double` cho tiền.

## 9. Database

- Schema do Flyway quản lý trong `src/main/resources/db/migration`; Hibernate dùng `ddl-auto=validate`, không dùng `update`.
- Mỗi thay đổi schema phải có một file migration mới. Không sửa migration đã áp dụng, kể cả seed; bổ sung migration mới để sửa dữ liệu.
- Version migration phải duy nhất. Thống nhất version với nhóm hoặc dùng timestamp và kiểm tra không trùng trước khi merge. Không sửa checksum hoặc chạy repair để che thay đổi migration đã áp dụng.
- Khi đổi entity (thêm/đổi/xóa cột, bảng, ràng buộc), phải nêu rõ trong mô tả PR để cả nhóm biết cập nhật DB local.
- Không tự ý đổi kiểu cột, xóa cột hoặc xóa bảng khi chưa hỏi.
- Không bao giờ chạy lệnh phá hủy (`DROP`, `TRUNCATE`, `DELETE` không điều kiện) trên database đang dùng. Test dùng Testcontainers, không dùng DB local.
- Seed trong `src/main/resources/db/seed` chỉ chạy qua Flyway với profile `dev`, mỗi version một lần; `spring.sql.init` tắt. Khi bổ sung seed phải giữ tổng hóa đơn đúng công thức, thanh toán khớp ca thu ngân, khóa ngoại hợp lệ và chạy lại `RestosphereBeSpringbootApplicationTests`. Không kích hoạt profile `dev` ở production.

## 10. Bảo mật

- Không commit bí mật: `.env`, mật khẩu, khóa API, token. `.env` đã bị git ignore; chỉ cập nhật `.env.example` (không chứa giá trị thật).
- Không ghi log mật khẩu, token, thông tin thanh toán.
- Mật khẩu `123456` của tài khoản `@restosphere.test` chỉ dành cho dữ liệu mẫu, không dùng ở production.

## 11. Test

- Logic nghiệp vụ mới hoặc sửa lỗi phải có test. Sửa lỗi thì viết test tái hiện lỗi trước.
- Test tích hợp đặt trong `src/test/java/com/vertex/restosphere/`, đặt tên `XxxApiIntegrationTests` hoặc `XxxTests`, dùng Testcontainers (`TestcontainersConfiguration`).
- Không sửa test cho pass bằng cách nới điều kiện. Test sai do yêu cầu đổi thì sửa có lý do và nêu trong PR.
- Không xóa test để làm build pass.

## 12. Git và quy trình

- Nhánh: `feat/<tên>`, `fix/<tên>`, `refactor/<tên>` tạo từ `develop`. `main` chỉ dùng cho release.
- Commit: `<loại>(<phạm vi>): <mô tả>` với loại là `feat`, `fix`, `refactor`, `chore`, `test`, `docs`. Ví dụ: `fix(cashier): reject payment above outstanding amount`. Mô tả ngắn, tiếng Anh, thì hiện tại.
- Mỗi commit làm một việc, build và test pass.
- PR mô tả: làm gì, vì sao, ảnh hưởng FE/DB (nếu có), cách kiểm tra.
- Giải quyết conflict phải xem kỹ cả hai phía; không chọn một bên mà làm mất code của người khác. Sau khi merge phải chạy lại build và test.

## 13. Quyền của agent

Agent **được** đọc code, sửa code trong phạm vi yêu cầu, chạy build/test/format, và **commit cục bộ chỉ khi thành viên yêu cầu rõ ràng**. Không tự commit khi hoàn thành công việc.

Agent **không được**:
- Force-push, đổi lịch sử (`reset --hard`, `rebase` lên nhánh chung, `commit --amend` commit đã push).
- Chạy lệnh phá hủy dữ liệu hoặc đổi database đang dùng.
- Sửa `.env` hoặc đọc/in giá trị bí mật.
- Xóa chức năng hoặc file của thành viên khác khi chưa kiểm tra và hỏi.
- Thêm thư viện, package, endpoint công khai hoặc thay đổi kiến trúc khi chưa hỏi.
- Bỏ qua lỗi build/test hoặc nói "đã kiểm tra" khi chưa chạy.

## 14. Cách agent làm việc

1. Đọc code liên quan và grep cách hiện có trước khi viết. Làm theo mẫu đang có trong repo.
2. Thay đổi nhỏ nhất đủ giải quyết yêu cầu; không refactor ngoài phạm vi, không thêm tính năng không được yêu cầu.
3. Chạy `spotless:apply` rồi `test`. Báo rõ cái gì đã **chạy thật** và cái gì **chỉ phân tích**.
4. Gặp chỗ chưa rõ (yêu cầu mơ hồ, quy tắc nghiệp vụ, đổi API/DB) thì **hỏi**, không đoán.
5. Báo ngắn gọn: đã đổi gì, ở file nào, đã kiểm tra gì, còn rủi ro gì.

## 15. File cá nhân và file dùng chung

- File cá nhân gồm thiết lập IDE riêng, cấu hình agent riêng, cache, báo cáo chạy thử, file tạm và file hệ điều hành. Đặt báo cáo/cache cá nhân trong `/.local/`, file tạm trong `/.tmp/`; cấu hình agent riêng dùng `opencode.local.json`, `opencode.local.jsonc`, `.opencode/*.local.json`, `.opencode/*.local.jsonc` hoặc `AGENTS.local.md`. Đây là quy ước đặt tên để ignore, không bảo đảm công cụ tự nạp cấu hình.
- File dùng chung phải giữ khả năng theo dõi: `AGENTS.md`, `README.md`, `.env.example`, cấu hình formatter, lockfile, source, test, seed, `scripts/`, `docs/`, cấu hình opencode dùng chung và workflow GitHub. Không ignore cả thư mục chỉ vì có một file cá nhân; không coi script chưa rõ mục đích (như `api-performance-probe.mjs`, `runtime-performance-probe.mjs`) là file cá nhân.
- Không đọc/in bí mật hoặc sửa `.env`. Không thêm ngoại lệ để đưa bí mật vào Git.
- `.gitignore` không bỏ theo dõi file đã tracked. Kiểm tra bằng `git ls-files -ci --exclude-standard` ở từng repo và báo các đường dẫn cần xem xét; **không chạy `git rm --cached` nếu chưa được chấp thuận rõ ràng**. Không xóa file hoặc thay đổi staging của người khác.
- Khi sửa ignore, dùng `git check-ignore -v --no-index <đường-dẫn>` để kiểm tra cả file cá nhân và file dùng chung; xem diff trước khi kết luận. Chỉ commit khi được yêu cầu rõ ràng, không push.
