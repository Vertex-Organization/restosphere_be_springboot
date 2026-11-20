package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.ApprovalDecisionRequest;
import com.vertex.restosphere.dto.response.ApprovalResponse;
import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerApprovalService;
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

@Tag(name = "Manager - Approvals")
@RestController
@RequestMapping("/api/v1/manager/approvals")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class ManagerApprovalController {

    private final ManagerApprovalService service;

    @GetMapping
    public List<ApprovalResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ApprovalRequest.Status status,
            @RequestParam(required = false) ApprovalRequest.Type type) {
        return service.list(CurrentUserService.idOf(jwt), status, type);
    }

    @PostMapping("/{id}/decision")
    public ApprovalResponse decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ApprovalDecisionRequest req) {
        return service.decide(CurrentUserService.idOf(jwt), id, req);
    }
}
