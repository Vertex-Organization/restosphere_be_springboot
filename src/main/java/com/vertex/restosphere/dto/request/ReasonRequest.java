package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason attached to a request (item cancellation, order priority...). */
public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
}
