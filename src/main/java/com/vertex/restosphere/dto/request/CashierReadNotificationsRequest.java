package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;

/** Null or empty ids means mark everything read (same as the legacy command). */
public record CashierReadNotificationsRequest(@Size(max = 500) List<String> ids) {}
