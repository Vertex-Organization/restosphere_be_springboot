package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Payment;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByBillIdAndStatus(UUID billId, Payment.Status status);

    boolean existsByReservationIdAndStatus(UUID reservationId, Payment.Status status);

    boolean existsByCashierShiftIdAndStatus(UUID shiftId, Payment.Status status);

    boolean existsByTransactionReference(String transactionReference);

    boolean existsByPayosOrderCode(Long payosOrderCode);

    Optional<Payment> findByPayosOrderCode(Long payosOrderCode);

    List<Payment> findByBillIdAndMethodAndStatus(
            UUID billId, Payment.Method method, Payment.Status status);

    List<Payment> findByReservationIdAndMethodAndStatus(
            UUID reservationId, Payment.Method method, Payment.Status status);

    List<Payment> findByBillIdInOrderByCreatedAtAsc(Collection<UUID> billIds);

    @Query(
            "select coalesce(sum(p.amount), 0) from Payment p where p.cashierShift.id = :shiftId "
                    + "and p.method = com.vertex.restosphere.entity.Payment.Method.CASH "
                    + "and p.status = com.vertex.restosphere.entity.Payment.Status.COMPLETED")
    BigDecimal sumCompletedCash(@Param("shiftId") UUID shiftId);

    @Query(
            "select p from Payment p where p.createdAt >= :from and p.createdAt <= :to and p.method"
                    + " in :methods and p.status in :statuses order by p.createdAt desc, p.id")
    List<Payment> search(
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to,
            @Param("methods") Collection<Payment.Method> methods,
            @Param("statuses") Collection<Payment.Status> statuses);
}
