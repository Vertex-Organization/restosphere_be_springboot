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
        name = "reservations",
        indexes = {
            @Index(name = "idx_reservations_starts_at", columnList = "starts_at"),
            @Index(name = "idx_reservations_status", columnList = "status"),
            @Index(name = "idx_reservations_area_status", columnList = "area_id, status")
        })
@Check(constraints = "guest_count > 0 and ends_at > starts_at")
public class Reservation extends BaseEntity {
    public enum Status {
        PENDING,
        CONFIRMED,
        REJECTED,
        CANCELLED,
        ARRIVED,
        SEATED,
        NO_SHOW,
        COMPLETED
    }

    private Integer actualGuests;
    private Instant arrivedAt;
    private Instant handedOverAt;
    private String specialRequests;

    public enum Source {
        WEB,
        PHONE,
        WALK_IN
    }

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String customerName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(length = 254)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @Column(nullable = false)
    private int guestCount;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(length = 1000)
    private String note;

    @Column(length = 150)
    private String occasion;
}
