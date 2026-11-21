package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.CashierAdjustmentRequestRequest;
import com.vertex.restosphere.dto.request.CashierReasonRequest;
import com.vertex.restosphere.dto.request.CashierReturnPaymentRequest;
import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierRequestResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.CashierRequestService;
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

@Tag(name = "Cashier - Requests")
@RestController
@RequestMapping("/api/v1/cashier")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
@RequiredArgsConstructor
public class CashierRequestController {
    private final CashierRequestService service;

    @GetMapping("/requests")
    public List<CashierRequestResponse> requests(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUserService.idOf(jwt));
    }

    @PostMapping("/bills/{id}/adjustment-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CashierRequestResponse adjustment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CashierAdjustmentRequestRequest req) {
        return service.requestAdjustment(CurrentUserService.idOf(jwt), id, req);
    }

    @PostMapping("/payments/{id}/void-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CashierRequestResponse voidPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CashierReasonRequest req) {
        return service.requestVoid(CurrentUserService.idOf(jwt), id, req);
    }

    @PostMapping("/requests/{id}/withdraw")
    public CashierRequestResponse withdraw(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.withdraw(CurrentUserService.idOf(jwt), id);
    }

    @PostMapping("/bills/{id}/return-payment-request")
    public CashierBillResponse returnPaymentRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CashierReturnPaymentRequest req) {
        return service.returnPaymentRequest(CurrentUserService.idOf(jwt), id, req);
    }
}
