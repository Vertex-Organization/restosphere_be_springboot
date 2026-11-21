package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.response.ManagerActivityResponse;
import com.vertex.restosphere.dto.response.ManagerPaymentResponse;
import com.vertex.restosphere.dto.response.ManagerSessionResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerOverviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Manager - Overview")
@RestController
@RequestMapping("/api/v1/manager")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class ManagerOverviewController {

    private final ManagerOverviewService service;

    @GetMapping("/sessions")
    public List<ManagerSessionResponse> sessions(@AuthenticationPrincipal Jwt jwt) {
        return service.sessions(CurrentUserService.idOf(jwt));
    }

    @GetMapping("/payments")
    public List<ManagerPaymentResponse> payments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        return service.payments(CurrentUserService.idOf(jwt), date);
    }

    @GetMapping("/activity")
    public List<ManagerActivityResponse> activity(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "50") int limit) {
        return service.activity(CurrentUserService.idOf(jwt), entityId, limit);
    }
}
