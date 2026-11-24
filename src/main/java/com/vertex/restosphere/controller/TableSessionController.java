package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.MergeSessionsRequest;
import com.vertex.restosphere.dto.request.SplitSessionRequest;
import com.vertex.restosphere.dto.response.SessionChangeResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.TableSessionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Table sessions")
@RestController
@RequestMapping("/api/v1/table-sessions/{sessionId}")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST','WAITER','CASHIER')")
@RequiredArgsConstructor
public class TableSessionController {

    private final TableSessionService service;

    @PostMapping("/merge")
    public SessionChangeResponse merge(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @RequestBody MergeSessionsRequest req) {
        return service.merge(CurrentUserService.idOf(jwt), sessionId, req.sourceSessionId());
    }

    @PostMapping("/split")
    public SessionChangeResponse split(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SplitSessionRequest req) {
        return service.split(
                CurrentUserService.idOf(jwt),
                sessionId,
                req.targetTableId(),
                req.itemIds(),
                req.guestCount());
    }
}
