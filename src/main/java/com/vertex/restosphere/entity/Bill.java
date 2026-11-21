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
@Table(name = "bills")
@Check(
        constraints =
                "subtotal >= 0 and discount >= 0 and tax_amount >= 0 and service_charge >= 0 and"
                        + " total >= 0 and total = subtotal - discount + tax_amount + service_charge")
public class Bill extends BaseEntity {
    @Version private long version;
    private Instant requestedAt;
    private String discountMode;
    private BigDecimal discountValue;
    private String discountReason;
    private String discountAppliedBy;
    private Instant discountAppliedAt;

    public enum Status {
        OPEN,
        PAID,
        VOIDED
    }

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private TableSession session;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal serviceCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    private Instant paidAt;
}
