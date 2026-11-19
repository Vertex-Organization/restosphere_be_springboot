package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.ManagerReadNotificationsRequest;
import com.vertex.restosphere.dto.response.ManagerNotificationResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerNotificationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Manager - Notifications")
@RestController
@RequestMapping("/api/v1/manager/notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class ManagerNotificationController {

    private final ManagerNotificationService service;

    @GetMapping
    public List<ManagerNotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUserService.idOf(jwt));
    }

    /** Khong body / {} / ids rong = doc tat ca. */
    @PostMapping("/read")
    public List<ManagerNotificationResponse> markRead(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) ManagerReadNotificationsRequest req) {
        return service.markRead(CurrentUserService.idOf(jwt), req == null ? null : req.ids());
    }
}
