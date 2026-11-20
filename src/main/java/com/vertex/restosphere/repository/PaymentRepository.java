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

    boolean existsByCashierShiftIdAndStatus(UUID shiftId, Payment.Status status);

    boolean existsByTransactionReference(String transactionReference);

    // Payments of many bills in one query (no N+1).
    List<Payment> findByBillIdInOrderByCreatedAtAsc(Collection<UUID> billIds);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.cashierShift.id = :shiftId "
            + "and p.method = com.vertex.restosphere.entity.Payment.Method.CASH "
            + "and p.status = com.vertex.restosphere.entity.Payment.Status.COMPLETED")
    BigDecimal sumCompletedCash(@Param("shiftId") UUID shiftId);
}
