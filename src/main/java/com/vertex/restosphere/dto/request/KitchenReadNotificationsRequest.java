package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;

public record KitchenReadNotificationsRequest(@Size(max = 500) List<String> ids) {}
