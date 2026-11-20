package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.ShiftAssignment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {

    // Ten cac ca dang dien ra (startsAt < now < endsAt) cua nhan vien, ca som nhat truoc.
    @Query("select s.name from ShiftAssignment a join a.shift s "
            + "where a.user.id = :userId and s.startsAt < :now and s.endsAt > :now order by s.startsAt, s.id")
    List<String> findCurrentShiftNames(@Param("userId") UUID userId, @Param("now") Instant now);

    boolean existsByShiftIdAndUserId(UUID shiftId, UUID userId);

    // Cashier: schedule of every cashier, shift and user loaded in the same query.
    @Query("select a from ShiftAssignment a join fetch a.shift s join fetch a.user u "
            + "where u.role = com.vertex.restosphere.common.enums.Role.CASHIER order by s.startsAt, a.id")
    List<ShiftAssignment> findCashierAssignments();
}
