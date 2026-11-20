package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.ReservationTable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationTableRepository extends JpaRepository<ReservationTable, UUID> {

    boolean existsByTableIdAndReservationStatusIn(UUID tableId, Collection<Reservation.Status> statuses);

    @Query("select distinct rt.table.id from ReservationTable rt where rt.reservation.status in :statuses")
    Set<UUID> findTableIdsByReservationStatusIn(Collection<Reservation.Status> statuses);

    @Query("select rt from ReservationTable rt join fetch rt.table where rt.reservation.id in :ids")
    List<ReservationTable> findWithTableByReservationIdIn(Collection<UUID> ids);
}
