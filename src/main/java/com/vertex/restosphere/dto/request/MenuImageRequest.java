package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MenuImageRequest(
        @NotBlank String contentType, @NotBlank @Size(max = 7_000_000) String data) {}
