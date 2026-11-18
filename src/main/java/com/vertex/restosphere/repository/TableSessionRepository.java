package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.TableSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableSessionRepository extends JpaRepository<TableSession, UUID> {

    boolean existsByWaiterIdAndStatusNot(UUID waiterId, TableSession.Status status);
}
