package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Area;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRepository extends JpaRepository<Area, UUID> {

    boolean existsByName(String name);
}
