package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.CashierManualTransferRequest;
import com.vertex.restosphere.dto.request.CashierPayCashRequest;
import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.CashierPaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cashier - Payments")
@RestController
@RequestMapping("/api/v1/cashier")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
@RequiredArgsConstructor
public class CashierPaymentController {
    private final CashierPaymentService service;

    @GetMapping("/payments")
    public List<CashierPaymentResponse> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(required = false) Payment.Method method,
            @RequestParam(required = false) String status) {
        return service.history(CurrentUserService.idOf(jwt), from, to, method, status);
    }

    @PostMapping("/bills/{id}/payments/cash")
    public ResponseEntity<CashierPaymentResponse> payCash(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CashierPayCashRequest req) {
        return respond(service.payCash(CurrentUserService.idOf(jwt), id, req));
    }

    @PostMapping("/bills/{id}/payments/manual-transfer")
    public ResponseEntity<CashierPaymentResponse> manualTransfer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CashierManualTransferRequest req) {
        return respond(service.payManualTransfer(CurrentUserService.idOf(jwt), id, req));
    }

    private static ResponseEntity<CashierPaymentResponse> respond(
            CashierPaymentService.Outcome outcome) {
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(outcome.payment());
    }
}
