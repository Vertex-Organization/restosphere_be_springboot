package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.MenuCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {

    boolean existsByName(String name);
}
