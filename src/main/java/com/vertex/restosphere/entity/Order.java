package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Getter
@Setter
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "round_number"}))
@Check(constraints = "round_number > 0")
public class Order extends BaseEntity {
    private Instant priorityAt;
    private String priorityBy;
    private String priorityReason;
    public enum Status { DRAFT, SUBMITTED, COMPLETED, CANCELLED }
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TableSession session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "waiter_id", nullable = false)
    private User waiter;
    @Column(nullable = false)
    private int roundNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;
    @Column(length = 1000)
    private String note;
}
