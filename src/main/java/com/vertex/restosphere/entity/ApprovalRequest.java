package com.vertex.restosphere.entity;

import com.vertex.restosphere.common.enums.DiscountMode;
import com.vertex.restosphere.common.enums.MenuItemStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "approval_requests",
        indexes = {
            @Index(name = "idx_approval_requests_type_status", columnList = "type, status"),
            @Index(name = "idx_approval_requests_target_id", columnList = "target_id"),
            @Index(name = "idx_approval_requests_requested_by", columnList = "requested_by")
        })
public class ApprovalRequest extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DiscountMode discountMode;

    @Column(precision = 14, scale = 2)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MenuItemStatus menuStatus;

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
