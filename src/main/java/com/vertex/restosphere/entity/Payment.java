package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Getter
@Setter
@Entity
@Table(
        name = "payments",
        indexes = {
            @Index(name = "idx_payments_bill_status", columnList = "bill_id, status"),
            @Index(name = "idx_payments_shift_status", columnList = "cashier_shift_id, status"),
            @Index(name = "idx_payments_paid_at", columnList = "paid_at")
        })
@Check(
        constraints =
                "amount > 0 and (tendered is null or tendered >= 0)"
                        + " and (bill_id is not null or reservation_id is not null)"
                        + " and (method = 'PAYOS' or cashier_shift_id is not null)")
public class Payment extends BaseEntity {
    public enum Method {
        CASH,
        PAYOS,
        MANUAL_TRANSFER
    }

    public enum Status {
        PENDING,
        COMPLETED,
        FAILED,
        VOIDED,
        CANCELLED
    }

    @Column(unique = true, length = 150)
    private String idempotencyKey;

    @Column(precision = 14, scale = 0)
    private BigDecimal tendered;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Employee createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id")
    private Employee verifiedBy;

    // A deposit has no bill until the guest checks in; it then moves onto the table's bill.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    // Null for online payOS payments, which no cashier handles.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_shift_id")
    private CashierShift cashierShift;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(unique = true, length = 150)
    private String transactionReference;

    @Column(unique = true)
    private Long payosOrderCode;

    @Column(length = 500)
    private String checkoutUrl;

    @Column(length = 500)
    private String proofImageUrl;

    private Instant paidAt;

    @Column(length = 1000)
    private String note;
}
