package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;

/** In bill có QR payOS: link được tạo cho phần còn lại tính ở máy chủ, không nhận số tiền. */
public record CashierPayosLinkRequest(@NotNull Long billVersion) {}
