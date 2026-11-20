package com.vertex.restosphere.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record KitchenItemsRequest(@NotEmpty @Size(max = 50) List<@Valid KitchenItemRef> items) {}
