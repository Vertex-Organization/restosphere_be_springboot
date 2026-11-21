package com.vertex.restosphere.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RestaurantProfileRequest(
        @NotNull @Size(max = 150) String name,
        @Size(max = 255) String description,
        @Size(max = 255) String address,
        @Size(max = 30) String hotline,
        @Size(max = 255) String email,
        @Size(max = 255) String openingHours,
        @Size(max = 255) String facebook,
        @Size(max = 255) String instagram,
        @Size(max = 255) String tiktok) {}
