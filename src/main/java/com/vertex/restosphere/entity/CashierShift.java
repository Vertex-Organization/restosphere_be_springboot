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
        name = "cashier_shifts",
        indexes =
                @Index(
                        name = "idx_cashier_shifts_cashier_closed",
                        columnList = "cashier_id, closed_at"))
@Check(
        constraints =
                "opening_cash >= 0 and (counted_cash is null or counted_cash >= 0) and (closed_at"
                        + " is null or closed_at >= opened_at)")
public class CashierShift extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    private Shift scheduledShift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @Column(nullable = false)
    private Instant openedAt;

    private Instant closedAt;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal openingCash;

    @Column(precision = 14, scale = 0)
    private BigDecimal expectedCash;

    @Column(precision = 14, scale = 0)
    private BigDecimal countedCash;

    @Column(length = 1000)
    private String note;
}
