package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ReportQueryRepository extends Repository<Payment, UUID> {

    @Query(
            "select p from Payment p join fetch p.bill b "
                    + "where p.status = :status and coalesce(p.paidAt, p.createdAt) >= :from "
                    + "and coalesce(p.paidAt, p.createdAt) < :to")
    List<Payment> findPaymentsWithBillBetween(
            @Param("status") Payment.Status status,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(
            "select i from OrderItem i join i.order o where o.session.bill.id in :billIds and"
                    + " i.status not in :excluded")
    List<OrderItem> findItemsByBillIdIn(
            @Param("billIds") Collection<UUID> billIds,
            @Param("excluded") Collection<OrderItem.Status> excluded);

    @Query(
            "select r.status, count(r) from Reservation r where r.startsAt >= :from and r.startsAt"
                    + " < :to group by r.status")
    List<Object[]> countReservationsByStatus(@Param("from") Instant from, @Param("to") Instant to);
}
