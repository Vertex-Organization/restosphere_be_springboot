package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Getter
@Setter
@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "round_number"}))
@Check(
        constraints =
                "(status = 'DRAFT' and round_number is null)"
                        + " or (status = 'SUBMITTED' and round_number > 0)")
public class Order extends BaseEntity {
    public enum Status {
        DRAFT,
        SUBMITTED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TableSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "waiter_id", nullable = false)
    private Employee waiter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    private Integer roundNumber;

    // TODO(confirm): column outside the agreed design; kitchen needs the real send time, not cart
    // creation.
    private Instant submittedAt;

    private Instant priorityAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_by_id")
    private Employee priorityBy;

    @Column(length = 1000)
    private String priorityReason;
}
