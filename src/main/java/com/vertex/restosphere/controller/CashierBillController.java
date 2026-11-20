package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierConfigResponse;
import com.vertex.restosphere.dto.response.CashierReceiptResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.CashierBillService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cashier - Bills")
@RestController
@RequestMapping("/api/v1/cashier")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
@RequiredArgsConstructor
public class CashierBillController {

    private final CashierBillService service;

    /** status: UNPAID, PAYMENT_REQUESTED or PAID (optional). */
    @GetMapping("/bills")
    public List<CashierBillResponse> bills(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status) {
        return service.list(CurrentUserService.idOf(jwt), status);
    }

    @GetMapping("/bills/{id}")
    public CashierBillResponse bill(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.detail(CurrentUserService.idOf(jwt), id);
    }

    @GetMapping("/bills/{id}/receipt")
    public CashierReceiptResponse receipt(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.receipt(CurrentUserService.idOf(jwt), id);
    }

    @GetMapping("/config")
    public CashierConfigResponse config(@AuthenticationPrincipal Jwt jwt) {
        return service.config(CurrentUserService.idOf(jwt));
    }
}
