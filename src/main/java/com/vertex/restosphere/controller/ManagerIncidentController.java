package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.ManagerIncidentUpdateRequest;
import com.vertex.restosphere.dto.response.ManagerIncidentResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerIncidentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Manager - Incidents")
@RestController
@RequestMapping("/api/v1/manager/incidents")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class ManagerIncidentController {

    private final ManagerIncidentService service;

    @GetMapping
    public List<ManagerIncidentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUserService.idOf(jwt));
    }

    @PatchMapping("/{id}")
    public ManagerIncidentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ManagerIncidentUpdateRequest req) {
        return service.update(CurrentUserService.idOf(jwt), id, req);
    }
}
