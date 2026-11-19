package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.ReservationItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationItemRepository extends JpaRepository<ReservationItem, UUID> {

    boolean existsByMenuItemId(UUID menuItemId);
}
