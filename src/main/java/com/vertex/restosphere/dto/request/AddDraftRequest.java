package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Adds a menu item to the waiter's cart (quantity defaults to 1). */
public record AddDraftRequest(@NotNull UUID menuItemId) {
}
