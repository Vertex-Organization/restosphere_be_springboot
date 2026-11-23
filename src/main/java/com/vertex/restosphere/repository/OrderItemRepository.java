package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.TableSession;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    boolean existsByMenuItemId(UUID menuItemId);

    java.util.List<OrderItem> findByOrderSessionId(UUID sessionId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"menuItem", "chef"})
    java.util.List<OrderItem> findByOrderIdIn(java.util.Collection<UUID> orderIds);

    boolean existsByOrderSessionAndStatusIn(
            TableSession session, Collection<OrderItem.Status> statuses);

    /** Billable-side view of a bill: every session merged into it, carts not yet sent excluded. */
    @org.springframework.data.jpa.repository.Query(
            "select i from OrderItem i join i.order o where o.session.bill.id = :billId and"
                    + " i.status <> com.vertex.restosphere.entity.OrderItem.Status.DRAFT order by"
                    + " i.createdAt asc")
    java.util.List<OrderItem> findSentByBillId(
            @org.springframework.data.repository.query.Param("billId") UUID billId);

    @org.springframework.data.jpa.repository.Query(
            "select i from OrderItem i join fetch i.order o join fetch o.session s join fetch s.bill"
                    + " where i.id in :ids")
    java.util.List<OrderItem> findWithOrderByIdIn(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
}
