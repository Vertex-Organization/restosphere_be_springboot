package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.MenuImageRequest;
import com.vertex.restosphere.dto.response.MenuImageResponse;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.MenuImageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Menu - Images")
@RestController
@RequestMapping("/api/v1/menu/images")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class MenuImageController {

    private final MenuImageService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuImageResponse upload(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MenuImageRequest req) {
        return service.upload(CurrentUserService.idOf(jwt), req);
    }
}
