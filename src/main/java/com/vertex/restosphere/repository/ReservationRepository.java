package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Reservation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ReservationRepository
        extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    // Nap khu vuc cung query de lay ten khu khong bi N+1.
    @Override
    @EntityGraph(attributePaths = "area")
    List<Reservation> findAll(Specification<Reservation> spec, Sort sort);

    /** Reservations reception handed to the cashier for a cash deposit that is still unpaid. */
    @Query(
            "select r from Reservation r where r.status = com.vertex.restosphere.entity.Reservation.Status.PENDING"
                    + " and r.depositAmount is not null and r.depositCashRequestedAt is not null"
                    + " and r.depositDueAt > :now and not exists (select 1 from Payment p where"
                    + " p.reservation = r and p.status = com.vertex.restosphere.entity.Payment.Status.COMPLETED)"
                    + " order by r.depositCashRequestedAt")
    List<Reservation> findAwaitingCashDeposit(java.time.Instant now);

    /** Rows of [phone, reservation count, latest arrivedAt] for the given phone numbers. */
    @Query(
            "select r.phone, count(r), max(r.arrivedAt) from Reservation r"
                    + " where r.phone in :phones group by r.phone")
    List<Object[]> summarizeByPhoneIn(Collection<String> phones);

    @EntityGraph(attributePaths = "area")
    List<Reservation> findByPhoneInOrderByStartsAtDesc(Collection<String> phones, Pageable page);
}
