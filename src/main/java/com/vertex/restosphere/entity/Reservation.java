package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
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

    // null: khách không yêu cầu khu, lễ tân tự xếp bàn.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    public String areaName() {
        return area == null ? "Không yêu cầu khu" : area.getName();
    }

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

    /** Required deposit in whole VND; null when the booking needs none. */
    @Column(precision = 14, scale = 0)
    private BigDecimal depositAmount;

    private Instant depositDueAt;

    // Set when reception hands the guest to the cashier to pay the deposit in cash.
    private Instant depositCashRequestedAt;

    // Inverse side: deposit state is read from payments, never stored on the reservation.
    @OneToMany(mappedBy = "reservation")
    private Set<Payment> payments = new LinkedHashSet<>();

    public Instant depositPaidAt() {
        return payments.stream()
                .filter(p -> p.getStatus() == Payment.Status.COMPLETED)
                .map(Payment::getPaidAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    public boolean awaitingDeposit() {
        return depositAmount != null
                && payments.stream().noneMatch(p -> p.getStatus() == Payment.Status.COMPLETED);
    }

    /** The payOS link a guest can still pay, or null once the deposit is paid or no link exists. */
    public Payment livePayosPayment() {
        if (!awaitingDeposit()) return null;
        return payments.stream()
                .filter(
                        p ->
                                p.getMethod() == Payment.Method.PAYOS
                                        && p.getStatus() == Payment.Status.PENDING)
                .findFirst()
                .orElse(null);
    }

    /** Bank transfer note customers must use so staff can match the deposit. */
    public String depositReference() {
        return "COC " + code.replace("-", "");
    }
}
