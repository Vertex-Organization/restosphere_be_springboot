package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.MenuItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    boolean existsByCategoryId(UUID categoryId);

    // Nap category cung query: danh sach can ten/thu tu danh muc, tranh N+1.
    @Override
    @EntityGraph(attributePaths = "category")
    List<MenuItem> findAll();

    @EntityGraph(attributePaths = "category")
    List<MenuItem> findByCategoryId(UUID categoryId);
}
