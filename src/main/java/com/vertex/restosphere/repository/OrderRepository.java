package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.TableSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Don trong ngay + don cua phien chua dong; nap san ban, khu, waiter (khong N+1).
    @EntityGraph(attributePaths = {"session.table.area", "waiter"})
    @Query(
            "select o from Order o where o.status = :submitted and ((o.createdAt >= :from and o.createdAt < :to) "
                    + "or o.session.status <> :closed) order by o.createdAt asc, o.id asc")
    List<Order> findForManager(
            @Param("submitted") Order.Status submitted,
            @Param("closed") TableSession.Status closed,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @EntityGraph(attributePaths = {"session.table.area", "waiter"})
    @Query("select o from Order o where o.status = :status order by o.submittedAt asc")
    List<Order> findAllWithTable(@Param("status") Order.Status status);

    @Query("select coalesce(max(o.roundNumber), 0) from Order o where o.session = :session")
    int findLastRoundNumber(TableSession session);

    Optional<Order> findBySessionAndWaiterAndStatus(
            TableSession session, Employee waiter, Order.Status status);
}
