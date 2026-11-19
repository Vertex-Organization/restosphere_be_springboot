package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

/** Một lượt gọi món đã gửi bếp. Món chưa gửi nằm ở {@link OrderDraft}. */
@Getter
@Setter
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "round_number"}))
@Check(constraints = "round_number > 0")
public class Order extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TableSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "waiter_id", nullable = false)
    private User waiter;

    /** Lượt gọi thứ mấy của bàn, bắt đầu từ 1. */
    @Column(nullable = false)
    private int roundNumber;

    private Instant priorityAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_by_id")
    private User priorityBy;

    @Column(length = 1000)
    private String priorityReason;
}
