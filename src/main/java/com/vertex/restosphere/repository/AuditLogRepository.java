package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @Override
    @EntityGraph(attributePaths = "actor")
    Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    java.util.List<AuditLog> findByTargetTypeAndTargetIdInAndActionInOrderByCreatedAtAsc(
            String targetType,
            java.util.Collection<java.util.UUID> targetIds,
            java.util.Collection<String> actions);

    @Query(
            "select a.targetId, a.actor.id from AuditLog a where a.targetType = 'RESERVATION'"
                    + " and a.targetId in :ids order by a.createdAt desc")
    java.util.List<Object[]> findActorsByReservationIdIn(java.util.Collection<UUID> ids);

    java.util.List<AuditLog> findByTargetIdInOrderByCreatedAtAsc(
            java.util.Collection<java.util.UUID> targetIds);
}
