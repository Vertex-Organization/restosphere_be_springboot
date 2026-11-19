package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.response.KitchenOrderResponse;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.KitchenService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Status sai dinh dang -> 400 qua ResponseEntityExceptionHandler. */
@Tag(name = "Kitchen - Orders")
@RestController
@RequestMapping("/api/v1/kitchen")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('ADMIN','KITCHEN')")
@RequiredArgsConstructor
public class KitchenController {

    private final KitchenService service;

    @GetMapping("/orders")
    public List<KitchenOrderResponse> orders(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OrderItem.Status status) {
        return service.listOrders(CurrentUserService.idOf(jwt), status);
    }
}
