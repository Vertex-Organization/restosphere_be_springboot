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
@Check(constraints = "amount > 0 and (tendered is null or tendered >= 0)")
public class Payment extends BaseEntity {
    public enum Method {
        CASH,
        TRANSFER
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

    @Column(length = 254)
    private String createdBy;

    @Column(length = 254)
    private String verifiedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_shift_id", nullable = false)
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

    private Instant paidAt;

    @Column(length = 1000)
    private String note;
}
