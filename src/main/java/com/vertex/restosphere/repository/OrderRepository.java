package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Order;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Nap san ban, khu, waiter de dung phieu bep khong bi N+1.
    @EntityGraph(attributePaths = {"session.table.area", "waiter"})
    List<Order> findByStatusNotOrderByCreatedAtAsc(Order.Status status);
}
