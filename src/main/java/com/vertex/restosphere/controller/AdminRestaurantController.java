package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.RestaurantImageRequest;
import com.vertex.restosphere.dto.request.RestaurantProfileRequest;
import com.vertex.restosphere.dto.response.RestaurantProfileResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.AdminRestaurantService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Restaurant")
@RestController
@RequestMapping("/api/v1/admin/restaurant")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRestaurantController {

    private final AdminRestaurantService service;

    @GetMapping
    public RestaurantProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(CurrentUserService.idOf(jwt));
    }

    @PutMapping
    public RestaurantProfileResponse update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RestaurantProfileRequest req) {
        return service.update(CurrentUserService.idOf(jwt), req);
    }

    @PostMapping("/images/{kind}")
    public RestaurantProfileResponse uploadImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String kind,
            @Valid @RequestBody RestaurantImageRequest req) {
        return service.uploadImage(CurrentUserService.idOf(jwt), kind, req);
    }

    @DeleteMapping("/images/{kind}")
    public RestaurantProfileResponse removeImage(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String kind) {
        return service.removeImage(CurrentUserService.idOf(jwt), kind);
    }
}
