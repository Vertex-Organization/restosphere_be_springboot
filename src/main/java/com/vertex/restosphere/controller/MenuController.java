package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.MenuCategoryRequest;
import com.vertex.restosphere.dto.request.MenuItemRequest;
import com.vertex.restosphere.dto.request.MenuItemStatusRequest;
import com.vertex.restosphere.dto.response.MenuCategoryResponse;
import com.vertex.restosphere.dto.response.MenuItemResponse;
import com.vertex.restosphere.service.MenuService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** GET la cong khai (web khach hang); ?all=true can quyen ADMIN/MANAGER de thay ca muc dang an. */
@Tag(name = "Menu")
@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
public class MenuController {

    private static final String MANAGE = "hasAnyRole('ADMIN','MANAGER')";

    private final MenuService service;

    @GetMapping("/categories")
    @PreAuthorize("!#all or hasAnyRole('ADMIN','MANAGER')")
    public List<MenuCategoryResponse> categories(@RequestParam(defaultValue = "false") boolean all) {
        return service.listCategories(all);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize(MANAGE)
    public MenuCategoryResponse createCategory(@Valid @RequestBody MenuCategoryRequest req) {
        return service.createCategory(req);
    }

    @PutMapping("/categories/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize(MANAGE)
    public MenuCategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody MenuCategoryRequest req) {
        return service.updateCategory(id, req);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize(MANAGE)
    public void deleteCategory(@PathVariable UUID id) {
        service.deleteCategory(id);
    }

    @GetMapping("/items")
    @PreAuthorize("!#all or hasAnyRole('ADMIN','MANAGER')")
    public List<MenuItemResponse> items(@RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "false") boolean all) {
        return service.listItems(categoryId, all);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize(MANAGE)
    public MenuItemResponse createItem(@Valid @RequestBody MenuItemRequest req) {
        return service.createItem(req);
    }

    @PutMapping("/items/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize(MANAGE)
    public MenuItemResponse updateItem(@PathVariable UUID id, @Valid @RequestBody MenuItemRequest req) {
        return service.updateItem(id, req);
    }

    // shortcut: bep doi thang trang thai, UI hien tai co luong "yeu cau duyet"; them khi lam approval_requests.
    @PatchMapping("/items/{id}/status")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','KITCHEN')")
    public MenuItemResponse updateItemStatus(@PathVariable UUID id, @Valid @RequestBody MenuItemStatusRequest req) {
        return service.updateItemStatus(id, req.status());
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize(MANAGE)
    public void deleteItem(@PathVariable UUID id) {
        service.deleteItem(id);
    }
}
