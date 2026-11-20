package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.CashierMeResponse;
import com.vertex.restosphere.dto.response.CashierNotificationResponse;
import com.vertex.restosphere.entity.Notification;
import com.vertex.restosphere.entity.NotificationRead;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.NotificationReadRepository;
import com.vertex.restosphere.repository.NotificationRepository;
import com.vertex.restosphere.repository.ShiftAssignmentRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Notifications and the profile of the signed-in cashier (REST), same rules as the legacy read-notifications. */
@Service
@RequiredArgsConstructor
public class CashierNotificationService {

    private static final List<String> PERMISSIONS = List.of("bills.view", "bills.prepare", "payments.create",
            "payments.view", "payments.confirm_transfer", "receipts.print", "transactions.view", "discounts.apply",
            "discounts.request", "requests.create", "shifts.view", "shifts.manage");
    private static final int DISCOUNT_LIMIT_PERCENT = 10;

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final CashierWriteSupport writes;
    private final NotificationRepository notifications;
    private final NotificationReadRepository reads;
    private final ShiftAssignmentRepository shifts;

    @Transactional(readOnly = true)
    public List<CashierNotificationResponse> list(UUID actorId) {
        User user = authorize(actorId);
        return toResponses(notifications.findVisible(user.getRole(), user));
    }

    /** ids null or empty reads everything; unknown ids are ignored; already-read rows are not written twice. */
    @Transactional
    public List<CashierNotificationResponse> markRead(UUID actorId, List<String> ids) {
        User user = authorize(actorId);
        restaurantLock.acquire();
        List<Notification> visible = notifications.findVisible(user.getRole(), user);
        boolean all = ids == null || ids.isEmpty();
        Set<UUID> wanted = new HashSet<>();
        if (!all) {
            for (String raw : ids) {
                try {
                    wanted.add(UUID.fromString(raw == null ? "" : raw.trim()));
                } catch (IllegalArgumentException ignored) {
                    // Malformed ids match nothing, like the legacy command.
                }
            }
        }
        Set<UUID> alreadyRead = reads.findReadNotificationIds(user);
        for (Notification n : visible) {
            if ((all || wanted.contains(n.getId())) && !alreadyRead.contains(n.getId())) {
                NotificationRead read = new NotificationRead();
                read.setNotification(n);
                read.setUser(user);
                read.setReadAt(Instant.now());
                reads.save(read);
            }
        }
        writes.commit(user, "read-notifications");
        reads.flush();
        return toResponses(visible);
    }

    @Transactional(readOnly = true)
    public CashierMeResponse me(UUID actorId) {
        User u = authorize(actorId);
        List<String> current = shifts.findCurrentShiftNames(u.getId(), Instant.now());
        return new CashierMeResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getRole().name(),
                current.isEmpty() ? "" : current.getFirst(), PERMISSIONS, DISCOUNT_LIMIT_PERCENT);
    }

    private User authorize(UUID actorId) {
        return currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
    }

    private List<CashierNotificationResponse> toResponses(List<Notification> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UUID>> readers = new HashMap<>();
        for (Object[] row : reads.findReaders(found.stream().map(Notification::getId).toList())) {
            readers.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((UUID) row[1]);
        }
        return found.stream().map(n -> {
            boolean decision = "REQUEST_DECIDED".equals(n.getKind());
            return new CashierNotificationResponse(n.getId(), n.getKind(), n.getTitle(), n.getBody(),
                    n.getCreatedAt(), n.getAudienceRole() == Role.CASHIER && !decision ? n.getPath() : null,
                    decision ? n.getPath() : null, readers.getOrDefault(n.getId(), List.of()));
        }).toList();
    }
}
