package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * type/severity la String de service tra dung loi 400 "Invalid incident type"/"Invalid severity" nhu lenh cu
 * (severity chap nhan ca CRITICAL). orderId/itemId la chuoi: id sai dinh dang -> 400, khong ton tai -> 404.
 */
public record KitchenIncidentRequest(@NotBlank String type, @NotBlank String severity, @NotBlank String description,
        @Size(max = 100) String orderId, @Size(max = 100) String itemId) {}
