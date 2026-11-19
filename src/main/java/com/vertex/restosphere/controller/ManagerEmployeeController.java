package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.AssignEmployeeAreaRequest;
import com.vertex.restosphere.dto.response.ManagerEmployeeResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerEmployeeService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Manager - Employees")
@RestController
@RequestMapping("/api/v1/manager/employees")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class ManagerEmployeeController {

    private final ManagerEmployeeService service;

    @GetMapping
    public List<ManagerEmployeeResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUserService.idOf(jwt));
    }

    @PatchMapping("/{id}/area")
    public ManagerEmployeeResponse assignArea(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody AssignEmployeeAreaRequest req) {
        return service.assignArea(CurrentUserService.idOf(jwt), id, req);
    }
}
