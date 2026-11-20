package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.response.ManagerTableResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.TableService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Manager - Tables")
@RestController
@RequestMapping("/api/v1/manager/tables")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class ManagerTableController {

    private final TableService tableService;

    @GetMapping
    public List<ManagerTableResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return tableService.listForManager(CurrentUserService.idOf(jwt));
    }
}
