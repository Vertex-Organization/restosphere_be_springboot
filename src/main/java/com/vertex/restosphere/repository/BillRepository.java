package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Bill;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    boolean existsByStatus(Bill.Status status);

    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"sessions", "sessions.table", "sessions.table.area"})
    java.util.List<Bill> findAllByOrderByCreatedAtAsc();

    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {
                "sessions",
                "sessions.table",
                "sessions.table.area",
                "sessions.waiter"
            })
    java.util.Optional<Bill> findDetailedById(UUID id);
}
