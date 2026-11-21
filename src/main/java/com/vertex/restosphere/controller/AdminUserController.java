package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.CreateUserRequest;
import com.vertex.restosphere.dto.request.UpdateUserRoleRequest;
import com.vertex.restosphere.dto.request.UpdateUserStatusRequest;
import com.vertex.restosphere.dto.response.AdminUserResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.AdminUserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Users")
@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService service;

    @GetMapping
    public List<AdminUserResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUserService.idOf(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateUserRequest req) {
        return service.create(CurrentUserService.idOf(jwt), req);
    }

    @PatchMapping("/{id}/status")
    public AdminUserResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest req) {
        return service.updateStatus(CurrentUserService.idOf(jwt), id, req.status());
    }

    @PatchMapping("/{id}/role")
    public AdminUserResponse updateRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRoleRequest req) {
        return service.updateRole(CurrentUserService.idOf(jwt), id, req.role());
    }
}
