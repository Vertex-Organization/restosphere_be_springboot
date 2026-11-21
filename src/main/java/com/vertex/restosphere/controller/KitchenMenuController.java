package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.KitchenMenuRequestCreate;
import com.vertex.restosphere.dto.request.KitchenMenuStatusRequest;
import com.vertex.restosphere.dto.response.KitchenMenuItemResponse;
import com.vertex.restosphere.dto.response.KitchenMenuRequestResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.KitchenMenuService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Kitchen - Menu availability")
@RestController
@RequestMapping("/api/v1/kitchen")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','KITCHEN')")
@RequiredArgsConstructor
public class KitchenMenuController {
    private final KitchenMenuService service;

    @GetMapping("/menu-items")
    public List<KitchenMenuItemResponse> menuItems(@AuthenticationPrincipal Jwt jwt) {
        return service.listItems(CurrentUserService.idOf(jwt));
    }

    @PatchMapping("/menu-items/{id}/status")
    public KitchenMenuItemResponse setStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody KitchenMenuStatusRequest req) {
        return service.setStatus(CurrentUserService.idOf(jwt), id, req.status());
    }

    @GetMapping("/menu-requests")
    public List<KitchenMenuRequestResponse> menuRequests(@AuthenticationPrincipal Jwt jwt) {
        return service.listRequests(CurrentUserService.idOf(jwt));
    }

    @PostMapping("/menu-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public KitchenMenuRequestResponse requestStatus(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody KitchenMenuRequestCreate req) {
        return service.request(CurrentUserService.idOf(jwt), req.menuItemId().trim(), req.status());
    }
}
