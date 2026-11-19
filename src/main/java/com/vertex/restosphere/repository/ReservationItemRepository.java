package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.ReservationItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationItemRepository extends JpaRepository<ReservationItem, UUID> {

    boolean existsByMenuItemId(UUID menuItemId);

    @Query("select i from ReservationItem i join fetch i.menuItem where i.reservation.id in :ids")
    List<ReservationItem> findWithMenuItemByReservationIdIn(Collection<UUID> ids);
}
