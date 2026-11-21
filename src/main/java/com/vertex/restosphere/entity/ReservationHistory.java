package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "reservation_histories")
public class ReservationHistory extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 50)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Reservation.Status previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Reservation.Status newStatus;

    @Column(nullable = false, length = 2000)
    private String description;
}
