package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.KitchenMeResponse;
import com.vertex.restosphere.dto.response.KitchenNotificationResponse;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Notification;
import com.vertex.restosphere.entity.NotificationRead;
import com.vertex.restosphere.repository.NotificationReadRepository;
import com.vertex.restosphere.repository.NotificationRepository;
import com.vertex.restosphere.repository.ShiftAssignmentRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.KitchenNotificationService;
import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.WorkspaceCommit;
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

@Service
@RequiredArgsConstructor
public class KitchenNotificationServiceImpl implements KitchenNotificationService {
    private static final List<String> PERMISSIONS =
            List.of(
                    "kitchen.items.update",
                    "kitchen.incidents.create",
                    "kitchen.menu.availability");

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final WorkspaceCommit writes;
    private final NotificationRepository notifications;
    private final NotificationReadRepository reads;
    private final ShiftAssignmentRepository shifts;

    @Transactional(readOnly = true)
    @Override
    public List<KitchenNotificationResponse> list(UUID actorId) {
        Employee user = authorize(actorId);
        return toResponses(notifications.findVisible(user.getRole(), user));
    }

    @Transactional
    @Override
    public List<KitchenNotificationResponse> markRead(UUID actorId, List<String> ids) {
        Employee user = authorize(actorId);
        restaurantLock.acquire();
        List<Notification> visible = notifications.findVisible(user.getRole(), user);
        boolean all = ids == null || ids.isEmpty();
        Set<UUID> wanted = new HashSet<>();
        if (!all) {
            for (String raw : ids) {
                try {
                    wanted.add(UUID.fromString(raw == null ? "" : raw.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        Set<UUID> alreadyRead = reads.findReadNotificationIds(user);
        for (Notification n : visible) {
            if ((all || wanted.contains(n.getId())) && !alreadyRead.contains(n.getId())) {
                NotificationRead read = new NotificationRead();
                read.setNotification(n);
                read.setEmployee(user);
                read.setReadAt(Instant.now());
                reads.save(read);
            }
        }
        writes.commit(user, "read-notifications");
        reads.flush();
        return toResponses(visible);
    }

    @Transactional(readOnly = true)
    @Override
    public KitchenMeResponse me(UUID actorId) {
        Employee u = authorize(actorId);
        List<String> current = shifts.findCurrentShiftNames(u.getId(), Instant.now());
        return new KitchenMeResponse(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getRole(),
                u.getRole().name(),
                current.isEmpty() ? "" : current.getFirst(),
                PERMISSIONS);
    }

    private Employee authorize(UUID actorId) {
        return currentUser.require(actorId, Role.ADMIN, Role.KITCHEN);
    }

    private List<KitchenNotificationResponse> toResponses(List<Notification> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UUID>> readers = new HashMap<>();
        for (Object[] row : reads.findReaders(found.stream().map(Notification::getId).toList())) {
            readers.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((UUID) row[1]);
        }
        return found.stream()
                .map(
                        n ->
                                new KitchenNotificationResponse(
                                        n.getId(),
                                        n.getKind(),
                                        n.getTitle(),
                                        n.getBody(),
                                        n.getCreatedAt(),
                                        n.getPath(),
                                        n.getAudienceRole() == Role.KITCHEN
                                                        && ("NEW_ORDER".equals(n.getKind())
                                                                || "PRIORITY".equals(n.getKind()))
                                                ? n.getPath()
                                                : null,
                                        readers.getOrDefault(n.getId(), List.of())))
                .toList();
    }
}
