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
@Table(name = "payments")
@Check(constraints = "amount > 0")
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

    @Column(unique = true)
    private String idempotencyKey;

    private BigDecimal tendered;
    private String createdBy;
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
