package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Reservation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReservationRepository
        extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    // Nap khu vuc cung query de lay ten khu khong bi N+1.
    @Override
    @EntityGraph(attributePaths = "area")
    List<Reservation> findAll(Specification<Reservation> spec, Sort sort);
}
