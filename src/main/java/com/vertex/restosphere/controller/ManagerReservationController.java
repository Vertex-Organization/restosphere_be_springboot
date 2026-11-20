package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.ChangeReservationTableRequest;
import com.vertex.restosphere.dto.response.ManagerReservationResponse;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerReservationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Manager - Reservations")
@RestController
@RequestMapping("/api/v1/manager/reservations")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class ManagerReservationController {

    private final ManagerReservationService service;

    @GetMapping
    public List<ManagerReservationResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Reservation.Status status) {
        return service.list(CurrentUserService.idOf(jwt), from, to, status);
    }

    @PatchMapping("/{id}/table")
    public ManagerReservationResponse changeTable(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ChangeReservationTableRequest req) {
        return service.changeTable(CurrentUserService.idOf(jwt), id, req);
    }
}
