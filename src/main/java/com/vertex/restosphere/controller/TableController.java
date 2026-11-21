package com.vertex.restosphere.controller;

import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.TableRequest;
import com.vertex.restosphere.dto.request.TableStatusRequest;
import com.vertex.restosphere.dto.response.TableResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.TableService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Tables")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService service;

    @GetMapping
    public List<TableResponse> list(
            @RequestParam(required = false) UUID areaId,
            @RequestParam(required = false) TableStatus status) {
        return service.list(areaId, status);
    }

    @GetMapping("/{id}")
    public TableResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public TableResponse create(@Valid @RequestBody TableRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public TableResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody TableRequest req) {
        return service.update(CurrentUserService.idOf(jwt), id, req);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST','WAITER')")
    public TableResponse updateStatus(
            @PathVariable UUID id, @Valid @RequestBody TableStatusRequest req) {
        return service.updateStatus(id, req.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
