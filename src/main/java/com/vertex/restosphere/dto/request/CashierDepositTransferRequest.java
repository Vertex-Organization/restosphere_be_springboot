package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cọc chuyển khoản thủ công khi payOS lỗi; quản lý nhập email + mật khẩu xác nhận ngay tại quầy.
 */
public record CashierDepositTransferRequest(
        @NotBlank String idempotencyKey,
        @NotBlank @Size(max = 150) String transactionReference,
        @Size(max = 500) String proofImageUrl,
        @NotBlank String managerEmail,
        @NotBlank String managerPassword) {}
