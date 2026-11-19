package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;

/** ids null/rong = doc het thong bao cua nguoi goi (giong lenh cu). */
public record KitchenReadNotificationsRequest(@Size(max = 500) List<String> ids) {}
