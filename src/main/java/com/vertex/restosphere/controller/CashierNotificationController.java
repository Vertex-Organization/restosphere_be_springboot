package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.CashierReadNotificationsRequest;
import com.vertex.restosphere.dto.response.CashierMeResponse;
import com.vertex.restosphere.dto.response.CashierNotificationResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.CashierNotificationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cashier - Notifications")
@RestController
@RequestMapping("/api/v1/cashier")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
@RequiredArgsConstructor
public class CashierNotificationController {
    private final CashierNotificationService service;

    @GetMapping("/notifications")
    public List<CashierNotificationResponse> notifications(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUserService.idOf(jwt));
    }

    @PostMapping("/notifications/read")
    public List<CashierNotificationResponse> read(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) CashierReadNotificationsRequest req) {
        return service.markRead(CurrentUserService.idOf(jwt), req == null ? null : req.ids());
    }

    @GetMapping("/me")
    public CashierMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return service.me(CurrentUserService.idOf(jwt));
    }
}
