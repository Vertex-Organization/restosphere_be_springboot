package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.KitchenIncidentRequest;
import com.vertex.restosphere.dto.response.KitchenIncidentResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.KitchenIncidentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Kitchen - Incidents")
@RestController
@RequestMapping("/api/v1/kitchen/incidents")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','KITCHEN')")
@RequiredArgsConstructor
public class KitchenIncidentController {
    private final KitchenIncidentService service;

    @GetMapping
    public List<KitchenIncidentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUserService.idOf(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KitchenIncidentResponse report(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody KitchenIncidentRequest req) {
        return service.report(CurrentUserService.idOf(jwt), req);
    }
}
