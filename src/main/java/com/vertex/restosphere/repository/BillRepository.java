package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Bill;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    boolean existsByStatus(Bill.Status status);

    // Cashier: bills with session, table and area in one query.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"session", "session.table",
            "session.table.area"})
    java.util.List<Bill> findAllByOrderByCreatedAtAsc();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"session", "session.table",
            "session.table.area"})
    java.util.Optional<Bill> findDetailedById(UUID id);
}
