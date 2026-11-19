package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Shift;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// Loc theo khoang startsAt + sap xep qua Specification (giong ReservationRepository).
public interface ShiftRepository extends JpaRepository<Shift, UUID>, JpaSpecificationExecutor<Shift> {
}
