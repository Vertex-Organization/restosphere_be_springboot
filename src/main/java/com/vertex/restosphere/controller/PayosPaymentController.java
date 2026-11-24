package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.PayosPaymentQueries;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Kiểm tra lại": hỏi payOS trạng thái đơn của một payment đang chờ. */
@Tag(name = "payOS payments")
@RestController
@RequestMapping("/api/v1/payments")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RECEPTIONIST','WAITER')")
@RequiredArgsConstructor
public class PayosPaymentController {
    private final PayosPaymentQueries queries;

    @PostMapping("/{id}/refresh")
    public CashierPaymentResponse refresh(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return queries.refresh(CurrentUserService.idOf(jwt), id);
    }
}
