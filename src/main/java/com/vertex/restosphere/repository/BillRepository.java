package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Bill;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    boolean existsByStatus(Bill.Status status);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"session", "session.table",
            "session.table.area"})
    java.util.List<Bill> findAllByOrderByCreatedAtAsc();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"session", "session.table",
            "session.table.area", "session.waiter"})
    java.util.Optional<Bill> findDetailedById(UUID id);

    java.util.List<Bill> findBySessionIdIn(java.util.Collection<UUID> sessionIds);
}
