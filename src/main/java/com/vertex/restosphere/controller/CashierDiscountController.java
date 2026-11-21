package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.CashierDiscountRequest;
import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierRequestResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.CashierDiscountService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cashier - Discounts")
@RestController
@RequestMapping("/api/v1/cashier")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
@RequiredArgsConstructor
public class CashierDiscountController {
    private final CashierDiscountService service;

    @PutMapping("/bills/{id}/discount")
    public CashierBillResponse apply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CashierDiscountRequest req) {
        return service.apply(CurrentUserService.idOf(jwt), id, req);
    }

    @DeleteMapping("/bills/{id}/discount")
    public CashierBillResponse remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam long billVersion) {
        return service.remove(CurrentUserService.idOf(jwt), id, billVersion);
    }

    @PostMapping("/bills/{id}/discount-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CashierRequestResponse request(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CashierDiscountRequest req) {
        return service.request(CurrentUserService.idOf(jwt), id, req);
    }
}
