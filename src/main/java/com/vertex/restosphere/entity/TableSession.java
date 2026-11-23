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
        name = "table_sessions",
        indexes = {
            @Index(name = "idx_table_sessions_table_status", columnList = "table_id, status"),
            @Index(name = "idx_table_sessions_waiter_status", columnList = "waiter_id, status")
        })
@Check(constraints = "guest_count > 0 and (ended_at is null or ended_at >= started_at)")
public class TableSession extends BaseEntity {
    private Instant acceptedAt;
    private Instant guestLeftAt;

    public enum Status {
        OPEN,
        PAYMENT_REQUESTED,
        CLOSED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTable table;

    // Many sessions can share a reservation (one per table) and a bill (merged tables).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id")
    private Employee waiter;

    @Column(nullable = false)
    private int guestCount;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.OPEN;
}
