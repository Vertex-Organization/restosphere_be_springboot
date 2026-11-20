package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.ManagerNotificationResponse;
import com.vertex.restosphere.entity.Notification;
import com.vertex.restosphere.entity.NotificationRead;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.NotificationReadRepository;
import com.vertex.restosphere.repository.NotificationRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerNotificationService;
import com.vertex.restosphere.service.RestaurantLock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerNotificationServiceImpl implements ManagerNotificationService {

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final NotificationRepository notifications;
    private final NotificationReadRepository reads;

    @Transactional(readOnly = true)
    @Override
    public List<ManagerNotificationResponse> list(UUID actorId) {
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        return toResponses(notifications.findVisible(Role.MANAGER, actor));
    }

    @Transactional
    @Override
    public List<ManagerNotificationResponse> markRead(UUID actorId, List<UUID> ids) {
        restaurantLock.acquire();
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        List<Notification> visible = notifications.findVisible(Role.MANAGER, actor);
        Set<UUID> alreadyRead = reads.findReadNotificationIds(actor);
        boolean all = ids == null || ids.isEmpty();
        Set<UUID> wanted = all ? Set.of() : Set.copyOf(ids);
        Instant now = Instant.now();
        for (Notification n : visible) {
            if ((all || wanted.contains(n.getId())) && !alreadyRead.contains(n.getId())) {
                NotificationRead r = new NotificationRead();
                r.setNotification(n);
                r.setUser(actor);
                r.setReadAt(now);
                reads.save(r);
            }
        }
        reads.flush();
        return toResponses(visible);
    }

    private List<ManagerNotificationResponse> toResponses(List<Notification> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UUID>> readers = reads.findReaders(list.stream().map(Notification::getId).toList()).stream()
                .collect(Collectors.groupingBy(row -> (UUID) row[0],
                        Collectors.mapping(row -> (UUID) row[1], Collectors.toList())));
        return list.stream().map(n -> new ManagerNotificationResponse(n.getId(), n.getKind(), n.getTitle(),
                n.getBody(), n.getCreatedAt(), n.getPath(), readers.getOrDefault(n.getId(), List.of()))).toList();
    }
}
