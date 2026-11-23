package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.MenuGroup;
import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.common.enums.PriceUnit;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.MenuCategoryRequest;
import com.vertex.restosphere.dto.request.MenuItemRequest;
import com.vertex.restosphere.dto.response.MenuCategoryResponse;
import com.vertex.restosphere.dto.response.MenuItemResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.MenuCategory;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.MenuCategoryRepository;
import com.vertex.restosphere.repository.MenuItemRepository;
import com.vertex.restosphere.repository.OrderDraftRepository;
import com.vertex.restosphere.repository.OrderItemRepository;
import com.vertex.restosphere.repository.ReservationItemRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.MenuService;
import com.vertex.restosphere.service.RestaurantLock;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MenuServiceImpl implements MenuService {

    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final RestaurantLock restaurantLock;
    private final CurrentUserService currentUser;
    private final AuditLogRepository auditLogs;
    private final OrderItemRepository orderItems;
    private final OrderDraftRepository orderDrafts;
    private final ReservationItemRepository reservationItems;

    /** includeHidden=false: ban cong khai, chi danh muc dang hien. */
    @Transactional(readOnly = true)
    @Override
    public List<MenuCategoryResponse> listCategories(boolean includeHidden) {
        return categories.findAll().stream()
                .filter(c -> includeHidden || c.isVisible())
                .sorted(
                        Comparator.comparingInt(MenuCategory::getSortOrder)
                                .thenComparing(MenuCategory::getName))
                .map(MenuCategoryResponse::from)
                .toList();
    }

    // Ghi danh muc: khoa truoc roi moi kiem tra actor trong DB (giong phan Admin), de lenh
    // workspace cu
    // (category.save/delete cung khoa nay) va REST khong chen nhau khi kiem tra trung ten.
    @Override
    public MenuCategoryResponse createCategory(UUID actorId, MenuCategoryRequest req) {
        restaurantLock.acquire();
        Employee actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        String name = req.name().strip();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Category name already exists");
        }
        MenuCategory c = new MenuCategory();
        c.setName(name);
        c.setSortOrder(req.sortOrder());
        c.setVisible(req.visible());
        c.setMenuGroup(req.group() == null ? MenuGroup.FOOD : req.group());
        categories.save(c);
        audit(actor, "CATEGORY_CREATED", c, "Thêm danh mục ");
        return MenuCategoryResponse.from(c);
    }

    @Override
    public MenuCategoryResponse updateCategory(UUID actorId, UUID id, MenuCategoryRequest req) {
        restaurantLock.acquire();
        Employee actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        MenuCategory c = findCategory(id);
        String name = req.name().strip();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("Category name already exists");
        }
        MenuGroup group = req.group() == null ? c.getMenuGroup() : req.group();
        if (name.equals(c.getName())
                && req.sortOrder() == c.getSortOrder()
                && req.visible() == c.isVisible()
                && group == c.getMenuGroup()) {
            return MenuCategoryResponse.from(c);
        }
        c.setName(name);
        c.setSortOrder(req.sortOrder());
        c.setVisible(req.visible());
        c.setMenuGroup(group);
        audit(actor, "CATEGORY_UPDATED", c, "Cập nhật danh mục ");
        return MenuCategoryResponse.from(c);
    }

    @Override
    public void deleteCategory(UUID actorId, UUID id) {
        restaurantLock.acquire();
        Employee actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        MenuCategory c = findCategory(id);
        if (items.existsByCategoryId(id)) {
            throw ApiException.conflict("Category still has menu items");
        }
        categories.delete(c);
        audit(actor, "CATEGORY_DELETED", c, "Xóa danh mục ");
    }

    /** Trang Manager doc description lam "Hoat dong gan day" / "Lich su xu ly". */
    private void audit(Employee actor, String action, MenuCategory c, String prefix) {
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
    @Override
    public List<MenuItemResponse> listItems(UUID categoryId, boolean includeHidden) {
        List<MenuItem> result =
                categoryId != null ? items.findByCategoryId(categoryId) : items.findAll();
        return result.stream()
                .filter(
                        i ->
                                includeHidden
                                        || (i.getCategory().isVisible()
                                                && i.getStatus() != MenuItemStatus.DISCONTINUED))
                .sorted(
                        Comparator.comparingInt((MenuItem i) -> i.getCategory().getSortOrder())
                                .thenComparing(MenuItem::getName))
                .map(MenuItemResponse::from)
                .toList();
    }

    // Ghi mon: cung thu tu nhu danh muc (khoa -> actor trong DB -> kiem tra -> audit).
    @Override
    public MenuItemResponse createItem(UUID actorId, MenuItemRequest req) {
        restaurantLock.acquire();
        Employee actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        MenuItem item = new MenuItem();
        item.setName(req.name().strip());
        item.setCategory(visibleCategory(req.categoryId()));
        item.setPrice(req.price());
        item.setDescription(blankToNull(req.description()));
        item.setImageUrl(blankToNull(req.imageUrl()));
        item.setStatus(req.status() == null ? MenuItemStatus.AVAILABLE : req.status());
        item.setPriceUnit(req.unit() == null ? PriceUnit.PORTION : req.unit());
        // Flush ngay de response co updatedAt.
        items.saveAndFlush(item);
        auditItem(actor, "MENU_ITEM_CREATED", item.getId(), "Thêm món " + item.getName());
        return MenuItemResponse.from(item);
    }

    @Override
    public MenuItemResponse updateItem(UUID actorId, UUID id, MenuItemRequest req) {
        restaurantLock.acquire();
        Employee actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        MenuItem item = findItem(id);
        // Mon dang o danh muc an van sua duoc; chi chan khi chuyen SANG danh muc an.
        MenuCategory category =
                req.categoryId().equals(item.getCategory().getId())
                        ? item.getCategory()
                        : visibleCategory(req.categoryId());
        String name = req.name().strip();
        String description = blankToNull(req.description());
        String imageUrl = blankToNull(req.imageUrl());
        MenuItemStatus status = req.status() == null ? item.getStatus() : req.status();
        PriceUnit unit = req.unit() == null ? item.getPriceUnit() : req.unit();
        BigDecimal oldPrice = item.getPrice();
        boolean priceChanged = req.price().compareTo(oldPrice) != 0;
        if (name.equals(item.getName())
                && category == item.getCategory()
                && !priceChanged
                && Objects.equals(description, item.getDescription())
                && Objects.equals(imageUrl, item.getImageUrl())
                && status == item.getStatus()
                && unit == item.getPriceUnit()) {
            return MenuItemResponse.from(item);
        }
        item.setName(name);
        item.setCategory(category);
        item.setPrice(req.price());
        item.setDescription(description);
        item.setImageUrl(imageUrl);
        item.setStatus(status);
        item.setPriceUnit(unit);
        // Flush de updatedAt trong response la gia tri moi.
        items.flush();
        String text =
                "Cập nhật món "
                        + name
                        + (priceChanged
                                ? " (giá " + vnd(oldPrice) + " → " + vnd(req.price()) + ")"
                                : "");
        auditItem(actor, "MENU_ITEM_UPDATED", item.getId(), text);
        return MenuItemResponse.from(item);
    }

    @Override
    public MenuItemResponse updateItemStatus(UUID id, MenuItemStatus status) {
        MenuItem item = findItem(id);
        item.setStatus(status);
        return MenuItemResponse.from(item);
    }

    @Override
    public void deleteItem(UUID actorId, UUID id) {
        restaurantLock.acquire();
        Employee actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        MenuItem item = findItem(id);
        // Mon da vao don / nhap don / dat truoc thi giu lai de lich su va hoa don con tham chieu.
        if (orderItems.existsByMenuItemId(id)
                || orderDrafts.existsByMenuItemId(id)
                || reservationItems.existsByMenuItemId(id)) {
            throw ApiException.conflict("Menu item is in use, set it to DISCONTINUED instead");
        }
        items.delete(item);
        auditItem(actor, "MENU_ITEM_DELETED", id, "Xóa món " + item.getName());
    }

    private void auditItem(Employee actor, String action, UUID itemId, String description) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(action);
        log.setTargetType("MENU_ITEM");
        log.setTargetId(itemId);
        log.setDescription(description);
        auditLogs.save(log);
    }

    private MenuCategory visibleCategory(UUID id) {
        MenuCategory c = findCategory(id);
        if (!c.isVisible()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category is hidden");
        }
        return c;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** 250000 -> "250.000đ". */
    private static String vnd(BigDecimal amount) {
        return String.format(Locale.ROOT, "%,d", amount.longValue()).replace(',', '.') + "đ";
    }

    private MenuCategory findCategory(UUID id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
    }

    private MenuItem findItem(UUID id) {
        return items.findById(id).orElseThrow(() -> ApiException.notFound("Menu item"));
    }
}
