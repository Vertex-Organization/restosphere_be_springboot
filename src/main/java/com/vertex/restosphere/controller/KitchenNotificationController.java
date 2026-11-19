package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.KitchenReadNotificationsRequest;
import com.vertex.restosphere.dto.response.KitchenMeResponse;
import com.vertex.restosphere.dto.response.KitchenNotificationResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.KitchenNotificationService;
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

@Tag(name = "Kitchen - Notifications")
@RestController
@RequestMapping("/api/v1/kitchen")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','KITCHEN')")
@RequiredArgsConstructor
public class KitchenNotificationController {

    private final KitchenNotificationService service;

    @GetMapping("/notifications")
    public List<KitchenNotificationResponse> notifications(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUserService.idOf(jwt));
    }

    /** Khong co body hoac khong co ids = doc het; tra danh sach thong bao da cap nhat readBy. */
    @PostMapping("/notifications/read")
    public List<KitchenNotificationResponse> read(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) KitchenReadNotificationsRequest req) {
        return service.markRead(CurrentUserService.idOf(jwt), req == null ? null : req.ids());
    }

    @GetMapping("/me")
    public KitchenMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return service.me(CurrentUserService.idOf(jwt));
    }
}
