package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Chuyển khoản vào TK nhà hàng khi payOS lỗi; quản lý xác thực ngay tại quầy. */
public record CashierManualTransferRequest(
        @NotNull Long billVersion,
        @NotNull BigDecimal amount,
        @NotBlank String idempotencyKey,
        @NotBlank @Size(max = 150) String transactionReference,
        @Size(max = 500) String proofImageUrl,
        @NotBlank String managerEmail,
        @NotBlank String managerPassword) {}
