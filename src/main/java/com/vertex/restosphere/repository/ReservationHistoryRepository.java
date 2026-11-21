package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.ReservationHistory;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationHistoryRepository extends JpaRepository<ReservationHistory, UUID> {

    @Query(
            "select h.reservation.id, a.id from ReservationHistory h join h.actor a"
                    + " where h.reservation.id in :ids order by h.createdAt desc")
    List<Object[]> findActorsByReservationIdIn(Collection<UUID> ids);
}
