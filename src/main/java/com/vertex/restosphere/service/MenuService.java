package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.dto.request.MenuCategoryRequest;
import com.vertex.restosphere.dto.request.MenuItemRequest;
import com.vertex.restosphere.dto.response.MenuCategoryResponse;
import com.vertex.restosphere.dto.response.MenuItemResponse;
import com.vertex.restosphere.entity.MenuCategory;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.MenuCategoryRepository;
import com.vertex.restosphere.repository.MenuItemRepository;
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

    /** includeHidden=false: ban cong khai, chi danh muc dang hien. */
    @Transactional(readOnly = true)
    public List<MenuCategoryResponse> listCategories(boolean includeHidden) {
        return categories.findAll().stream()
                .filter(c -> includeHidden || c.isVisible())
                .sorted(Comparator.comparingInt(MenuCategory::getSortOrder))
                .map(MenuCategoryResponse::from).toList();
    }

    public MenuCategoryResponse createCategory(MenuCategoryRequest req) {
        String name = req.name().trim();
        if (categories.existsByName(name)) {
            throw ApiException.conflict("Category name already exists");
        }
        MenuCategory c = new MenuCategory();
        applyCategory(c, name, req);
        return MenuCategoryResponse.from(categories.save(c));
    }

    public MenuCategoryResponse updateCategory(UUID id, MenuCategoryRequest req) {
        MenuCategory c = findCategory(id);
        String name = req.name().trim();
        if (!name.equals(c.getName()) && categories.existsByName(name)) {
            throw ApiException.conflict("Category name already exists");
        }
        applyCategory(c, name, req);
        return MenuCategoryResponse.from(c);
    }

    public void deleteCategory(UUID id) {
        MenuCategory c = findCategory(id);
        if (items.existsByCategoryId(id)) {
            throw ApiException.conflict("Category still has menu items");
        }
        categories.delete(c);
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

    private void applyCategory(MenuCategory c, String name, MenuCategoryRequest req) {
        c.setName(name);
        c.setSortOrder(req.sortOrder());
        c.setVisible(req.visible());
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
