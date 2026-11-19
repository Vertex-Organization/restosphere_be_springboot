package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Incident;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    // Nap mon + order cung query: response can orderId/itemId, tranh N+1.
    @EntityGraph(attributePaths = {"reporter", "assignee", "orderItem", "orderItem.order"})
    List<Incident> findAllByOrderByCreatedAtAsc();
}
