package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.DiscountLimitRequest;
import com.vertex.restosphere.dto.response.DiscountLimitResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.DiscountLimitService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Discount limits")
@RestController
@RequestMapping("/api/v1/admin/settings/discount-limits")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDiscountLimitController {

    private final DiscountLimitService service;

    @GetMapping
    public DiscountLimitResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(CurrentUserService.idOf(jwt));
    }

    @PutMapping
    public DiscountLimitResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DiscountLimitRequest req) {
        return service.update(CurrentUserService.idOf(jwt), req);
    }
}
