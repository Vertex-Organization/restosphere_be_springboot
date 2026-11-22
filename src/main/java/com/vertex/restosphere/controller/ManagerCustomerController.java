package com.vertex.restosphere.controller;

import com.vertex.restosphere.common.enums.AccountStatus;
import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.CustomerRequest;
import com.vertex.restosphere.dto.request.UpdateUserStatusRequest;
import com.vertex.restosphere.dto.response.CustomerPageResponse;
import com.vertex.restosphere.dto.response.CustomerResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.CustomerService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Manager - Customers")
@RestController
@RequestMapping("/api/v1/manager/customers")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class ManagerCustomerController {

    private final CustomerService service;

    @GetMapping
    public CustomerPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUserService.idOf(jwt), q, status, page, size);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(CurrentUserService.idOf(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CustomerRequest req) {
        return service.create(CurrentUserService.idOf(jwt), req);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CustomerRequest req) {
        return service.update(CurrentUserService.idOf(jwt), id, req);
    }

    @PatchMapping("/{id}/status")
    public CustomerResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest req) {
        return service.updateStatus(CurrentUserService.idOf(jwt), id, req.status());
    }
}
