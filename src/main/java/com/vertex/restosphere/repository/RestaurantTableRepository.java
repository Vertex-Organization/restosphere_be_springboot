package com.vertex.restosphere.repository;

import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.RestaurantTable;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {

    boolean existsByCode(String code);

    boolean existsByAreaId(UUID areaId);

    List<RestaurantTable> findByAreaId(UUID areaId);

    List<RestaurantTable> findByStatus(TableStatus status);

    List<RestaurantTable> findByAreaIdAndStatus(UUID areaId, TableStatus status);
}
