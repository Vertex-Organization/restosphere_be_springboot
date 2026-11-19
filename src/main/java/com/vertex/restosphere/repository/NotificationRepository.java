package com.vertex.restosphere.repository;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Notification;
import com.vertex.restosphere.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // Thong bao gui cho vai tro cua user hoac gui rieng cho user (so sanh FK, khong join nen khong mat dong recipient null).
    @Query("select n from Notification n where n.audienceRole = :role or n.recipient = :user "
            + "order by n.createdAt desc, n.id desc")
    List<Notification> findVisible(@Param("role") Role role, @Param("user") User user);
}
