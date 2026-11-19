package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.TableSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TableSessionRepository extends JpaRepository<TableSession, UUID> {

    boolean existsByWaiterIdAndStatusNot(UUID waiterId, TableSession.Status status);

    boolean existsByTableIdAndStatusNot(UUID tableId, TableSession.Status status);

    /** Moi dong la [tableId, sessionId] cua phien chua dong. */
    @Query("select s.table.id, s.id from TableSession s where s.status <> com.vertex.restosphere.entity.TableSession.Status.CLOSED")
    List<Object[]> findOpenSessionIdsByTable();
}
