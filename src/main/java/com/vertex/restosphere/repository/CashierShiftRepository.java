package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.CashierShift;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashierShiftRepository extends JpaRepository<CashierShift, UUID> {

    boolean existsByCashierIdAndClosedAtIsNull(UUID cashierId);

    java.util.List<CashierShift> findAllByOrderByOpenedAtAsc();

    java.util.Optional<CashierShift> findFirstByCashierIdAndClosedAtIsNull(UUID cashierId);
}
