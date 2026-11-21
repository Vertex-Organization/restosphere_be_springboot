package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.NotificationRead;
import com.vertex.restosphere.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, UUID> {

    // Cap [notificationId, userId] cho nhieu thong bao mot query (khong N+1, khong nap entity
    // User).
    @Query(
            "select r.notification.id, r.user.id from NotificationRead r where r.notification.id in"
                    + " :ids")
    List<Object[]> findReaders(@Param("ids") Collection<UUID> notificationIds);

    @Query("select r.notification.id from NotificationRead r where r.user = :user")
    Set<UUID> findReadNotificationIds(@Param("user") User user);
}
