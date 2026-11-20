package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(@NotNull Boolean approve, @NotBlank @Size(max = 1000) String reason) {
}
