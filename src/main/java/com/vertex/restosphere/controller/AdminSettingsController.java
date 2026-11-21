package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.SystemSettingsRequest;
import com.vertex.restosphere.dto.response.SystemSettingsResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.AdminSettingsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Settings")
@RestController
@RequestMapping("/api/v1/admin/settings")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AdminSettingsService service;

    @GetMapping
    public SystemSettingsResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(CurrentUserService.idOf(jwt));
    }

    @PutMapping
    public SystemSettingsResponse update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SystemSettingsRequest req) {
        return service.update(CurrentUserService.idOf(jwt), req);
    }
}
