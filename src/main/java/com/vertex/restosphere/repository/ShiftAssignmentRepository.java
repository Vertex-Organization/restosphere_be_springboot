package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.ShiftAssignment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {

    @Query("select a from ShiftAssignment a join fetch a.user where a.shift.id in :shiftIds")
    List<ShiftAssignment> findWithUserByShiftIdIn(Collection<UUID> shiftIds);

    @Query(
            "select a from ShiftAssignment a join fetch a.shift s where a.user.id in :userIds"
                    + " and s.startsAt < :to and s.endsAt > :from")
    List<ShiftAssignment> findByUsersBetween(Collection<UUID> userIds, Instant from, Instant to);

    @Modifying(flushAutomatically = true)
    @Query("delete from ShiftAssignment a where a.shift.id = :shiftId")
    void deleteByShiftId(UUID shiftId);

    @Query(
            "select s.name from ShiftAssignment a join a.shift s where a.user.id = :userId and"
                    + " s.startsAt < :now and s.endsAt > :now order by s.startsAt, s.id")
    List<String> findCurrentShiftNames(@Param("userId") UUID userId, @Param("now") Instant now);

    boolean existsByShiftIdAndUserId(UUID shiftId, UUID userId);

    @Query(
            "select a from ShiftAssignment a join fetch a.shift s join fetch a.user u where u.role"
                    + " = com.vertex.restosphere.common.enums.Role.CASHIER order by s.startsAt, a.id")
    List<ShiftAssignment> findCashierAssignments();
}
