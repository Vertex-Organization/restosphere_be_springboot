package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.ReservationTable;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationTableRepository extends JpaRepository<ReservationTable, UUID> {

    boolean existsByTableIdAndReservationStatusIn(UUID tableId, Collection<Reservation.Status> statuses);

    // Danh sach ban cua Manager: 1 query cho tat ca ban co lich dang hieu luc.
    @Query("select distinct rt.table.id from ReservationTable rt where rt.reservation.status in :statuses")
    Set<UUID> findTableIdsByReservationStatusIn(Collection<Reservation.Status> statuses);
}
