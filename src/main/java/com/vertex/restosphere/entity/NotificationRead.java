package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "notification_reads",
        uniqueConstraints = @UniqueConstraint(columnNames = {"notification_id", "user_id"}),
        indexes = @Index(name = "idx_notification_reads_user_id", columnList = "user_id"))
public class NotificationRead extends ImmutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant readAt;
}
