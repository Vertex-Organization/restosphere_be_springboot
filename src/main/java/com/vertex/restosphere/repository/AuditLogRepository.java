package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    // Nap actor cung query de doc ten nguoi thuc hien khong bi N+1.
    @Override
    @EntityGraph(attributePaths = "actor")
    Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    java.util.List<AuditLog> findByTargetTypeAndTargetIdInAndActionInOrderByCreatedAtAsc(String targetType,
            java.util.Collection<java.util.UUID> targetIds, java.util.Collection<String> actions);
}
