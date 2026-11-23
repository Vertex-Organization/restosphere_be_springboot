package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.CreateEmployeeRequest;
import com.vertex.restosphere.dto.request.UpdateEmployeeRoleRequest;
import com.vertex.restosphere.dto.request.UpdateEmployeeStatusRequest;
import com.vertex.restosphere.dto.response.AdminEmployeeResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.AdminEmployeeService;
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
@RequestMapping("/api/v1/admin/employees")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminEmployeeController {

    private final AdminEmployeeService service;

    @GetMapping
    public List<AdminEmployeeResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUserService.idOf(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminEmployeeResponse create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateEmployeeRequest req) {
        return service.create(CurrentUserService.idOf(jwt), req);
    }

    @PatchMapping("/{id}/status")
    public AdminEmployeeResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeStatusRequest req) {
        return service.updateStatus(CurrentUserService.idOf(jwt), id, req.status());
    }

    @PatchMapping("/{id}/role")
    public AdminEmployeeResponse updateRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRoleRequest req) {
        return service.updateRole(CurrentUserService.idOf(jwt), id, req.role());
    }
}
