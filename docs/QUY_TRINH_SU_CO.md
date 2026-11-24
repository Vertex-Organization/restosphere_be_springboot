# Quy trình xử lý sự cố RestoSphere

## 1. Toàn bộ hệ thống ngừng hoạt động

- Quản lý ghi thời điểm bắt đầu sự cố, phân công lễ tân giữ sổ đặt bàn và thu ngân giữ sổ thu tiền. Đánh số chứng từ liên tục, không ghi mật khẩu hoặc thông tin đăng nhập ngân hàng.
- Lễ tân ghi tên khách, số điện thoại, giờ đến, số khách, bàn dự kiến, món đặt trước và số chứng từ cọc. Báo rõ **không hoàn cọc trong mọi trường hợp**, kể cả hủy hoặc không đến; ghi nhận khách đồng ý.
- Lễ tân không cầm tiền. Khách trả tiền mặt trực tiếp cho thu ngân; thu ngân ghi số tiền nhận, tiền thối, tiền cọc thực thu, người nhận và ca đang phụ trách; giao biên nhận có chữ ký.
- Chuyển khoản chỉ vào tài khoản chính thức của nhà hàng. Quản lý kiểm tra giao dịch trên ngân hàng, ghi mã giao dịch, số tiền, thời điểm, người xác nhận và lưu chứng từ. Ảnh khách đưa không thay thế việc kiểm tra tiền đã về.
- Phục vụ ghi món, số lượng, bàn và các lần gửi bếp trên phiếu; bếp xác nhận nhận phiếu để tránh làm trùng. Thu ngân tính bill và cọc đã thu từ chứng từ, quản lý kiểm tra trước khi chốt.
- Chứng từ có dữ liệu khách chỉ giao cho người có trách nhiệm, lưu kín theo quy định nhà hàng.

## 2. Khi hệ thống hoạt động lại

1. Quản lý đối chiếu sổ đặt bàn, phiếu món, biên nhận và sao kê; tra mã đặt bàn/giao dịch trước để tránh nhập trùng. Không nhập lại giao dịch payOS đã được hệ thống ghi COMPLETED.
2. Lễ tân nhập bù đặt bàn quầy/hotline, ghi số chứng từ và thời gian thực tế trong ghi chú. Nếu ngày đặt đã qua hoặc luồng hiện có không cho nhập bù, giữ chứng từ và lập yêu cầu quản lý xử lý; không lùi đồng hồ, sửa DB hoặc giả làm giao dịch hiện tại.
3. Thu ngân ghi cọc CASH hoặc MANUAL_TRANSFER qua luồng thu cọc với ca hợp lệ. Cọc CASH phải có quản lý đối chiếu chứng từ giấy; MANUAL_TRANSFER phải xác thực quản lý tại chỗ, nhập mã giao dịch và chứng từ theo API. Không tạo một khoản thu mới nếu khoản cũ đã tồn tại.
4. Nhập lại các món theo phiếu, kiểm tra không gửi bếp lần hai cho món đã làm. Nếu UI không hỗ trợ ghi nhận lịch sử an toàn, quản lý giữ danh sách chờ xử lý thay vì giả trạng thái.
5. Đối chiếu tổng thu, cọc, tiền mặt trong két, chuyển khoản và ca. Nếu ca gốc đã đóng, không gán tùy tiện vào ca khác; quản lý ghi chênh lệch và quyết định cách xử lý có dấu vết.
6. Lưu bảng đối chiếu gồm số chứng từ giấy → mã đặt bàn/bill/payment; quản lý ký xác nhận. Không xóa chứng từ sau khi nhập.

> TODO(confirm): API hiện chưa có luồng nhập bù hồi tố chuyên biệt hoặc xác nhận quản lý cho cọc CASH. Tạm dùng quản lý ký đối chiếu ngoài hệ thống; trường hợp vượt ràng buộc ngày/ca giữ chờ xử lý, không bỏ qua kiểm tra nghiệp vụ.

## 3. Chỉ payOS ngừng hoạt động

- Đặt bàn online: không báo đặt thành công nếu tạo link thất bại; hiển thị hotline từ cài đặt nhà hàng. Lễ tân nhận thông tin qua điện thoại, không hứa đã nhận cọc khi chưa có giao dịch.
- Tại quầy: khách chọn CASH qua thu ngân hoặc MANUAL_TRANSFER bằng QR tĩnh của tài khoản chính thức. Quản lý kiểm tra ngân hàng, xác thực ngay, ghi mã giao dịch và chứng từ. Không nhận tiền vào tài khoản cá nhân.
- Nếu đã có QR payOS PENDING, bấm **Kiểm tra lại** trước khi thu bằng cách khác. Không thu thêm chỉ vì thiếu webhook. Nếu không thể xác minh/hủy link an toàn, giữ chờ và báo quản lý, không đánh dấu đã trả bằng tay.
- Khi payOS hồi phục, kiểm tra lại các payment còn PENDING. Không đóng ca để bỏ qua payment chưa rõ trạng thái.
- Bill thay đổi sau khi in: link cũ phải hủy; tạo và in lại QR đúng số tiền. Không dùng ảnh QR cũ.
- Tiền về sau khi link/đặt bàn đã hủy: quản lý xem thông báo và sao kê, giữ nguyên trạng thái hủy để xử lý riêng; không tự khôi phục đặt bàn, không tự hoàn tiền.

## 4. Bằng chứng kiểm thử

Dùng Testcontainers cho backend và gateway giả với endpoint fake-paid trong môi trường dev riêng. Không thử bằng DB `restosphere`, không gọi payOS thật trong đợt tự động. Kiểm thử UI, thiết bị và các luồng chưa hoàn tất được ghi rõ trong `REFACTOR_PROGRESS.md`; tài liệu này không xác nhận chúng đã chạy thành công.
