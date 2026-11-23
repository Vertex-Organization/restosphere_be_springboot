package com.vertex.restosphere.entity;

import com.vertex.restosphere.common.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "notifications",
        indexes = {
            @Index(name = "idx_notifications_audience_role", columnList = "audience_role"),
            @Index(name = "idx_notifications_recipient_id", columnList = "recipient_id"),
            @Index(name = "idx_notifications_created_at", columnList = "created_at")
        })
public class Notification extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false, length = 50)
    private String kind;

    @Column(length = 500)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role audienceRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private Employee recipient;
}
