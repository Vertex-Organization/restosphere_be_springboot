package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** ids null hoac rong = danh dau tat ca thong bao nguoi goi thay. */
public record ManagerReadNotificationsRequest(@Size(max = 500) List<UUID> ids) {
}
