package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.NotificationRead;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, UUID> {

    // Cap [notificationId, userId] cho nhieu thong bao mot query (khong N+1, khong nap entity
    // Employee).
    @Query(
            "select r.notification.id, r.employee.id from NotificationRead r where r.notification.id in"
                    + " :ids")
    List<Object[]> findReaders(@Param("ids") Collection<UUID> notificationIds);

    @Query("select r.notification.id from NotificationRead r where r.employee = :user")
    Set<UUID> findReadNotificationIds(@Param("user") Employee user);
}
