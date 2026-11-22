package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Dùng cho khách tự đăng ký và cho quản lý thêm/sửa; {@code note} chỉ quản lý mới ghi. */
public record CustomerRequest(
        @NotBlank(message = "Vui lòng nhập họ tên.")
                @Size(max = 150, message = "Họ tên tối đa 150 ký tự.")
                String fullName,
        @NotBlank(message = "Vui lòng nhập số điện thoại.")
                @Pattern(regexp = "^\\+?[0-9 ().-]{8,20}$", message = "Số điện thoại không hợp lệ.")
                String phone,
        @Email(message = "Email không hợp lệ.")
                @Size(max = 254, message = "Email tối đa 254 ký tự.")
                String email,
        @PastOrPresent(message = "Ngày sinh không hợp lệ.") LocalDate birthday,
        Boolean marketingConsent,
        @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự.") String note) {

    public CustomerRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null || email.isBlank() ? null : email.strip();
        note = note == null || note.isBlank() ? null : note.strip();
        marketingConsent = Boolean.TRUE.equals(marketingConsent);
    }
}
