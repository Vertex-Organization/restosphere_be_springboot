package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Incident;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    // Nap mon + order cung query: response can orderId/itemId, tranh N+1.
    @EntityGraph(attributePaths = {"reporter", "assignee", "orderItem", "orderItem.order"})
    List<Incident> findAllByOrderByCreatedAtAsc();

    // Nap reporter, assignee, mon, order, phien, ban trong 1 query: response can nhan Ban/Luot/Ten
    // mon.
    @EntityGraph(
            attributePaths = {
                "reporter",
                "assignee",
                "orderItem",
                "orderItem.order",
                "orderItem.order.session",
                "orderItem.order.session.table"
            })
    List<Incident> findAllByOrderByCreatedAtDesc();

    @EntityGraph(
            attributePaths = {
                "reporter",
                "assignee",
                "orderItem",
                "orderItem.order",
                "orderItem.order.session",
                "orderItem.order.session.table"
            })
    Optional<Incident> findWithDetailsById(UUID id);
}
