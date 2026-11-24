package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentStatusRequest(
        @NotNull Long orderCode,
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 30) String phone) {}
