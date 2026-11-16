package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.MenuItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    boolean existsByCategoryId(UUID categoryId);

    List<MenuItem> findByCategoryId(UUID categoryId);

    List<MenuItem> findByCategoryVisibleTrue();
}
