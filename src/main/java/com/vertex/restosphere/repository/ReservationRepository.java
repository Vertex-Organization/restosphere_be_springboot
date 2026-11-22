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

    /** Rows of [phone, reservation count, latest arrivedAt] for the given phone numbers. */
    @Query(
            "select r.phone, count(r), max(r.arrivedAt) from Reservation r"
                    + " where r.phone in :phones group by r.phone")
    List<Object[]> summarizeByPhoneIn(Collection<String> phones);

    @EntityGraph(attributePaths = "area")
    List<Reservation> findByPhoneInOrderByStartsAtDesc(Collection<String> phones, Pageable page);
}
