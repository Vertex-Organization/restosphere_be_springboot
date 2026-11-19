package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.TableSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Nap san ban, khu, waiter de dung phieu bep khong bi N+1.
    @EntityGraph(attributePaths = {"session.table.area", "waiter"})
    List<Order> findByStatusNotOrderByCreatedAtAsc(Order.Status status);

    // Don trong ngay + don cua phien chua dong; nap san ban, khu, waiter (khong N+1).
    @EntityGraph(attributePaths = {"session.table.area", "waiter"})
    @Query("select o from Order o where o.status <> :draft and ((o.createdAt >= :from and o.createdAt < :to) "
            + "or o.session.status <> :closed) order by o.createdAt asc, o.id asc")
    List<Order> findForManager(@Param("draft") Order.Status draft, @Param("closed") TableSession.Status closed,
            @Param("from") Instant from, @Param("to") Instant to);
}
