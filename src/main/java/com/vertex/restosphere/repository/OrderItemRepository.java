package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.OrderItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    boolean existsByMenuItemId(UUID menuItemId);

    // Nap san menuItem va chef de phieu bep khong bi N+1.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"menuItem", "chef"})
    java.util.List<OrderItem> findByOrderIdIn(java.util.Collection<UUID> orderIds);

    // Cashier: every line of a table session (all orders), oldest first.
    java.util.List<OrderItem> findByOrderSessionIdOrderByCreatedAtAsc(UUID sessionId);

    // Cashier: lines with their order loaded (to reach the session without N+1).
    @org.springframework.data.jpa.repository.Query("select i from OrderItem i join fetch i.order where i.id in :ids")
    java.util.List<OrderItem> findWithOrderByIdIn(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
}
