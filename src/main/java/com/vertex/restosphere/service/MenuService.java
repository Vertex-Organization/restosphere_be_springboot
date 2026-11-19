package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.MenuCategoryRequest;
import com.vertex.restosphere.dto.request.MenuItemRequest;
import com.vertex.restosphere.dto.response.MenuCategoryResponse;
import com.vertex.restosphere.dto.response.MenuItemResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.MenuCategory;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.MenuCategoryRepository;
import com.vertex.restosphere.repository.MenuItemRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MenuService {

    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final RestaurantLock restaurantLock;
    private final CurrentUserService currentUser;
    private final AuditLogRepository auditLogs;

    /** includeHidden=false: ban cong khai, chi danh muc dang hien. */
    @Transactional(readOnly = true)
    public List<MenuCategoryResponse> listCategories(boolean includeHidden) {
        return categories.findAll().stream()
                .filter(c -> includeHidden || c.isVisible())
                .sorted(Comparator.comparingInt(MenuCategory::getSortOrder).thenComparing(MenuCategory::getName))
                .map(MenuCategoryResponse::from).toList();
    }

    // Ghi danh muc: khoa truoc roi moi kiem tra actor trong DB (giong phan Admin), de lenh workspace cu
    // (category.save/delete cung khoa nay) va REST khong chen nhau khi kiem tra trung ten.
    public MenuCategoryResponse createCategory(UUID actorId, MenuCategoryRequest req) {
        restaurantLock.acquire();
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        String name = req.name().strip();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Category name already exists");
        }
        MenuCategory c = new MenuCategory();
        c.setName(name);
        c.setSortOrder(req.sortOrder());
        c.setVisible(req.visible());
        c.setMenuGroup(req.group() == null ? "food" : req.group());
        categories.save(c);
        audit(actor, "CATEGORY_CREATED", c, "Thêm danh mục ");
        return MenuCategoryResponse.from(c);
    }

    public MenuCategoryResponse updateCategory(UUID actorId, UUID id, MenuCategoryRequest req) {
        restaurantLock.acquire();
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        MenuCategory c = findCategory(id);
        String name = req.name().strip();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("Category name already exists");
        }
        String group = req.group() == null ? c.getMenuGroup() : req.group();
        if (name.equals(c.getName()) && req.sortOrder() == c.getSortOrder() && req.visible() == c.isVisible()
                && group.equals(c.getMenuGroup())) {
            return MenuCategoryResponse.from(c);
        }
        c.setName(name);
        c.setSortOrder(req.sortOrder());
        c.setVisible(req.visible());
        c.setMenuGroup(group);
        audit(actor, "CATEGORY_UPDATED", c, "Cập nhật danh mục ");
        return MenuCategoryResponse.from(c);
    }

    public void deleteCategory(UUID actorId, UUID id) {
        restaurantLock.acquire();
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        MenuCategory c = findCategory(id);
        if (items.existsByCategoryId(id)) {
            throw ApiException.conflict("Category still has menu items");
        }
        categories.delete(c);
        audit(actor, "CATEGORY_DELETED", c, "Xóa danh mục ");
    }

    /** Trang Manager doc description lam "Hoat dong gan day" / "Lich su xu ly". */
    private void audit(User actor, String action, MenuCategory c, String prefix) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(action);
        log.setTargetType("MENU_CATEGORY");
        log.setTargetId(c.getId());
        log.setDescription(prefix + c.getName());
        auditLogs.save(log);
    }

    /** includeHidden=false: ban cong khai, bo mon cua danh muc an va mon ngung ban. */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> listItems(UUID categoryId, boolean includeHidden) {
        List<MenuItem> result = categoryId != null ? items.findByCategoryId(categoryId) : items.findAll();
        return result.stream()
                .filter(i -> includeHidden
                        || (i.getCategory().isVisible() && i.getStatus() != MenuItemStatus.DISCONTINUED))
                .map(MenuItemResponse::from).toList();
    }

    public MenuItemResponse createItem(MenuItemRequest req) {
        MenuItem item = new MenuItem();
        applyItem(item, req);
        return MenuItemResponse.from(items.save(item));
    }

    public MenuItemResponse updateItem(UUID id, MenuItemRequest req) {
        MenuItem item = findItem(id);
        applyItem(item, req);
        return MenuItemResponse.from(item);
    }

    public MenuItemResponse updateItemStatus(UUID id, MenuItemStatus status) {
        MenuItem item = findItem(id);
        item.setStatus(status);
        return MenuItemResponse.from(item);
    }

    public void deleteItem(UUID id) {
        items.delete(findItem(id));
    }

    private void applyItem(MenuItem item, MenuItemRequest req) {
        item.setName(req.name().trim());
        item.setPrice(req.price());
        item.setDescription(req.description());
        item.setImageUrl(req.imageUrl());
        item.setCategory(findCategory(req.categoryId()));
    }

    private MenuCategory findCategory(UUID id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
    }

    private MenuItem findItem(UUID id) {
        return items.findById(id).orElseThrow(() -> ApiException.notFound("Menu item"));
    }
}
