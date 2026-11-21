package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest extends BaseEntity {
    private String discountMode;
    private BigDecimal discountValue;
    private String menuStatus;

    public enum Type {
        DISCOUNT,
        CANCEL_ITEM,
        RESERVATION,
        BILL_ADJUSTMENT,
        PAYMENT_VOID,
        MENU_STATUS
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        WITHDRAWN
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(nullable = false, length = 50)
    private String targetType;

    @Column(nullable = false)
    private UUID targetId;

    @Column(precision = 14, scale = 0)
    private BigDecimal amount;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(length = 2000)
    private String decisionNote;

    private Instant decidedAt;
}
