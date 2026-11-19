package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.ManagerNotificationResponse;
import com.vertex.restosphere.entity.Notification;
import com.vertex.restosphere.entity.NotificationRead;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.NotificationReadRepository;
import com.vertex.restosphere.repository.NotificationRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thong bao cua trang Manager; thay snapshot "notifications" va lenh cu "notification.read" (lenh cu van giu).
 * ADMIN cung xem thong bao cua vai tro MANAGER; trang thai da doc tinh rieng tung nguoi. Khong ghi AuditLog.
 */
@Service
@RequiredArgsConstructor
public class ManagerNotificationService {

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final NotificationRepository notifications;
    private final NotificationReadRepository reads;

    @Transactional(readOnly = true)
    public List<ManagerNotificationResponse> list(UUID actorId) {
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        return toResponses(notifications.findVisible(Role.MANAGER, actor));
    }

    @Transactional
    public List<ManagerNotificationResponse> markRead(UUID actorId, List<UUID> ids) {
        // Khoa: bam 2 lan cung luc khong ghi trung vao unique (notification_id, user_id).
        restaurantLock.acquire();
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        List<Notification> visible = notifications.findVisible(Role.MANAGER, actor);
        Set<UUID> alreadyRead = reads.findReadNotificationIds(actor);
        boolean all = ids == null || ids.isEmpty();
        Set<UUID> wanted = all ? Set.of() : Set.copyOf(ids);
        Instant now = Instant.now();
        // Chi danh dau thong bao nguoi goi thay; id la/khong duoc thay thi bo qua (giong nhanh bep).
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

    /** Gom readBy cho ca danh sach bang 1 query. */
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
