package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.ShiftRequest;
import com.vertex.restosphere.dto.response.ShiftResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerShiftService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Ngay sai dinh dang -> 400 qua ResponseEntityExceptionHandler (giong A5/M4). */
@Tag(name = "Manager - Shifts")
@RestController
@RequestMapping("/api/v1/manager/shifts")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class ManagerShiftController {

    private final ManagerShiftService service;

    @GetMapping
    public List<ShiftResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(CurrentUserService.idOf(jwt), from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ShiftRequest req) {
        return service.create(CurrentUserService.idOf(jwt), req);
    }

    @PutMapping("/{id}")
    public ShiftResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ShiftRequest req) {
        return service.update(CurrentUserService.idOf(jwt), id, req);
    }
}
