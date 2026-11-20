package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.CashierCloseShiftRequest;
import com.vertex.restosphere.dto.request.CashierOpenShiftRequest;
import com.vertex.restosphere.dto.response.CashierScheduledShiftResponse;
import com.vertex.restosphere.dto.response.CashierShiftResponse;
import com.vertex.restosphere.entity.CashierShift;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.Shift;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.CashierShiftRepository;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.repository.ShiftAssignmentRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cashier shifts (REST): same rules as the legacy open-shift / close-shift commands. */
@Service
@RequiredArgsConstructor
public class CashierShiftService {

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final WorkspaceStore store;
    private final CashierWriteSupport writes;
    private final CashierShiftRepository shifts;
    private final ShiftAssignmentRepository assignments;
    private final PaymentRepository payments;

    /** Every cashier shift, like the legacy snapshot. */
    @Transactional(readOnly = true)
    public List<CashierShiftResponse> list(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        return shifts.findAllByOrderByOpenedAtAsc().stream().map(CashierShiftResponse::from).toList();
    }

    /** Assignments of every cashier, like the legacy snapshot. */
    @Transactional(readOnly = true)
    public List<CashierScheduledShiftResponse> scheduled(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        return assignments.findCashierAssignments().stream().map(CashierScheduledShiftResponse::from).toList();
    }

    @Transactional
    public CashierShiftResponse open(UUID actorId, CashierOpenShiftRequest req) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        String scheduledId = req.scheduledShiftId() == null ? "" : req.scheduledShiftId().trim();
        Shift scheduled = null;
        if (!scheduledId.isEmpty()) {
            scheduled = store.get(Shift.class, scheduledId);
            if (!assignments.existsByShiftIdAndUserId(scheduled.getId(), user.getId())) {
                throw WorkspaceStore.invalid("Scheduled shift is not assigned to this cashier");
            }
            Instant now = Instant.now();
            if (now.isBefore(scheduled.getStartsAt().minusSeconds(1800)) || now.isAfter(scheduled.getEndsAt())) {
                throw ApiException.conflict("Outside scheduled shift window");
            }
        }
        if (shifts.existsByCashierIdAndClosedAtIsNull(user.getId())) {
            throw ApiException.conflict("A shift is already open");
        }
        CashierShift shift = new CashierShift();
        shift.setCashier(user);
        shift.setOpenedAt(Instant.now());
        shift.setOpeningCash(CashierWriteSupport.money(req.openingFloat(), "openingFloat"));
        shift.setScheduledShift(scheduled);
        store.save(shift);
        writes.commit(user, "open-shift");
        store.flush();
        return CashierShiftResponse.from(shift);
    }

    @Transactional
    public CashierShiftResponse close(UUID actorId, UUID shiftId, CashierCloseShiftRequest req) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        CashierShift shift = store.get(CashierShift.class, shiftId.toString());
        if (!shift.getCashier().getId().equals(user.getId()) || shift.getClosedAt() != null) {
            throw ApiException.conflict("Shift is not open for this cashier");
        }
        if (payments.existsByCashierShiftIdAndStatus(shift.getId(), Payment.Status.PENDING)) {
            throw ApiException.conflict("Resolve pending transfers first");
        }
        BigDecimal cash = payments.sumCompletedCash(shift.getId());
        shift.setExpectedCash(shift.getOpeningCash().add(cash));
        shift.setCountedCash(CashierWriteSupport.money(req.countedCash(), "countedCash"));
        shift.setNote(req.note() == null ? "" : req.note().trim());
        shift.setClosedAt(Instant.now());
        writes.commit(user, "close-shift");
        store.flush();
        return CashierShiftResponse.from(shift);
    }
}
