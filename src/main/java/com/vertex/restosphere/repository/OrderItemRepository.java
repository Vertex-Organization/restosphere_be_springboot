package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.TableSession;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    boolean existsByMenuItemId(UUID menuItemId);

    // Nap san menuItem va chef de phieu bep khong bi N+1.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"menuItem", "chef"})
    java.util.List<OrderItem> findByOrderIdIn(java.util.Collection<UUID> orderIds);

    boolean existsByOrderSessionAndStatusIn(TableSession session, Collection<OrderItem.Status> statuses);
}
