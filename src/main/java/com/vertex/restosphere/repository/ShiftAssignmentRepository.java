package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.ShiftAssignment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {

    @Query("select a from ShiftAssignment a join fetch a.user where a.shift.id in :shiftIds")
    List<ShiftAssignment> findWithUserByShiftIdIn(Collection<UUID> shiftIds);

    /** Phan cong cua cac user o cac ca giao voi [from, to); service tu bo qua ca dang sua. */
    @Query("select a from ShiftAssignment a join fetch a.shift s where a.user.id in :userIds"
            + " and s.startsAt < :to and s.endsAt > :from")
    List<ShiftAssignment> findByUsersBetween(Collection<UUID> userIds, Instant from, Instant to);

    // Xoa ngay trong DB de chen lai cung (shift_id, user_id) khong vi pham unique.
    @Modifying(flushAutomatically = true)
    @Query("delete from ShiftAssignment a where a.shift.id = :shiftId")
    void deleteByShiftId(UUID shiftId);
}
