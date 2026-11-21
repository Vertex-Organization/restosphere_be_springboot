package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.TableSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ManagerQueryRepository extends Repository<TableSession, UUID> {

    @Query(
            "select s from TableSession s join fetch s.table left join fetch s.waiter "
                    + "where s.status <> :closed order by s.startedAt asc")
    List<TableSession> findOpenSessions(@Param("closed") TableSession.Status closed);

    @Query(
            "select p from Payment p where coalesce(p.paidAt, p.createdAt) >= :from and"
                    + " coalesce(p.paidAt, p.createdAt) < :to order by coalesce(p.paidAt, p.createdAt)"
                    + " asc")
    List<Payment> findPaymentsBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select a from AuditLog a left join fetch a.actor order by a.createdAt desc")
    List<AuditLog> findRecentActivity(Pageable page);

    @Query(
            "select a from AuditLog a left join fetch a.actor where a.targetId = :entityId order by"
                    + " a.createdAt desc")
    List<AuditLog> findActivityByTarget(@Param("entityId") UUID entityId, Pageable page);
}
