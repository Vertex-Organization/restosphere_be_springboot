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

/**
 * Doc so lieu cho bao cao Manager. Repository rieng de khong sua Bill/Reservation/OrderItemRepository
 * (nhanh feat/reception-customer-reservation dang sua, tranh conflict). Moi query lay ca khoang, khong N+1.
 */
public interface ReportQueryRepository extends Repository<Payment, UUID> {

    // Giao dich thanh cong trong khoang, nap san hoa don + phien (lay guestCount, paidAt) trong 1 query.
    @Query("select p from Payment p join fetch p.bill b join fetch b.session s "
            + "where p.status = :status and coalesce(p.paidAt, p.createdAt) >= :from "
            + "and coalesce(p.paidAt, p.createdAt) < :to")
    List<Payment> findPaymentsWithBillBetween(@Param("status") Payment.Status status,
            @Param("from") Instant from, @Param("to") Instant to);

    // Mon cua cac phien da thanh toan, bo mon huy/void/tu choi.
    @Query("select i from OrderItem i join i.order o where o.session.id in :sessionIds and i.status not in :excluded")
    List<OrderItem> findItemsBySessionIdIn(@Param("sessionIds") Collection<UUID> sessionIds,
            @Param("excluded") Collection<OrderItem.Status> excluded);

    // Dem dat ban theo trang thai trong khoang (gom o DB, khong nap tung dong).
    @Query("select r.status, count(r) from Reservation r where r.startsAt >= :from and r.startsAt < :to "
            + "group by r.status")
    List<Object[]> countReservationsByStatus(@Param("from") Instant from, @Param("to") Instant to);
}
