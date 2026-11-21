package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.CashierCloseShiftRequest;
import com.vertex.restosphere.dto.request.CashierOpenShiftRequest;
import com.vertex.restosphere.dto.response.CashierScheduledShiftResponse;
import com.vertex.restosphere.dto.response.CashierShiftResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.CashierShiftService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cashier - Shifts")
@RestController
@RequestMapping("/api/v1/cashier")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
@RequiredArgsConstructor
public class CashierShiftController {
    private final CashierShiftService service;

    @GetMapping("/shifts")
    public List<CashierShiftResponse> shifts(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUserService.idOf(jwt));
    }

    @GetMapping("/scheduled-shifts")
    public List<CashierScheduledShiftResponse> scheduled(@AuthenticationPrincipal Jwt jwt) {
        return service.scheduled(CurrentUserService.idOf(jwt));
    }

    @PostMapping("/shifts/open")
    @ResponseStatus(HttpStatus.CREATED)
    public CashierShiftResponse open(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CashierOpenShiftRequest req) {
        return service.open(CurrentUserService.idOf(jwt), req);
    }

    @PostMapping("/shifts/{id}/close")
    public CashierShiftResponse close(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CashierCloseShiftRequest req) {
        return service.close(CurrentUserService.idOf(jwt), id, req);
    }
}
