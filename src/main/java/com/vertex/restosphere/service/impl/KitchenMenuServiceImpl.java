package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.KitchenMenuItemResponse;
import com.vertex.restosphere.dto.response.KitchenMenuRequestResponse;
import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.ApprovalRequestRepository;
import com.vertex.restosphere.repository.MenuItemRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.KitchenMenuService;
import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WorkspaceCommit;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KitchenMenuServiceImpl implements KitchenMenuService {
    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final WorkspaceStore store;
    private final ServiceWorkflow workflow;
    private final WorkspaceCommit writes;
    private final MenuItemRepository menuItems;
    private final ApprovalRequestRepository approvals;

    @Transactional(readOnly = true)
    @Override
    public List<KitchenMenuItemResponse> listItems(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.KITCHEN);
        return menuItems.findAll().stream()
                .sorted(Comparator.comparing(MenuItem::getName).thenComparing(MenuItem::getId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public KitchenMenuItemResponse setStatus(
            UUID actorId, String menuItemId, MenuItemStatus status) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.KITCHEN);
        restaurantLock.acquire();
        MenuItem menu = store.get(MenuItem.class, menuItemId);
        validate(menu, status);
        menu.setStatus(status);
        workflow.notify(
                Role.WAITER,
                "MENU_AVAILABILITY",
                "Menu availability updated",
                menu.getName(),
                menu.getId().toString());
        writes.commit(user, "set-menu-status");
        store.flush();
        return toResponse(menu);
    }

    @Transactional(readOnly = true)
    @Override
    public List<KitchenMenuRequestResponse> listRequests(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.KITCHEN);
        return approvals.findByTypeOrderByCreatedAtAsc(ApprovalRequest.Type.MENU_STATUS).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public KitchenMenuRequestResponse request(
            UUID actorId, String menuItemId, MenuItemStatus status) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.KITCHEN);
        restaurantLock.acquire();
        MenuItem menu = store.get(MenuItem.class, menuItemId);
        validate(menu, status);
        if (approvals.existsByTypeAndTargetIdAndStatus(
                ApprovalRequest.Type.MENU_STATUS, menu.getId(), ApprovalRequest.Status.PENDING)) {
            throw ApiException.conflict("A request is already pending");
        }
        ApprovalRequest r = new ApprovalRequest();
        r.setType(ApprovalRequest.Type.MENU_STATUS);
        r.setTargetType("MENU_ITEM");
        r.setTargetId(menu.getId());
        r.setAmount(BigDecimal.ZERO);
        r.setReason("Menu availability: " + status);
        r.setMenuStatus(status);
        r.setRequestedBy(user);
        approvals.save(r);
        writes.commit(user, "request-menu-status");
        store.flush();
        return toResponse(r);
    }

    private static void validate(MenuItem menu, MenuItemStatus status) {
        if (status == MenuItemStatus.DISCONTINUED
                || menu.getStatus() == MenuItemStatus.DISCONTINUED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid menu status");
        }
    }

    private KitchenMenuItemResponse toResponse(MenuItem m) {
        return new KitchenMenuItemResponse(
                m.getId(),
                m.getName(),
                m.getCategory().getId(),
                m.getImageUrl(),
                m.getStatus(),
                m.getUpdatedAt());
    }

    private KitchenMenuRequestResponse toResponse(ApprovalRequest r) {
        return new KitchenMenuRequestResponse(
                r.getId(),
                r.getTargetId(),
                r.getMenuStatus(),
                r.getRequestedBy().getId(),
                r.getCreatedAt(),
                r.getStatus(),
                r.getDecidedAt(),
                r.getDecidedBy() == null ? null : r.getDecidedBy().getId());
    }
}
