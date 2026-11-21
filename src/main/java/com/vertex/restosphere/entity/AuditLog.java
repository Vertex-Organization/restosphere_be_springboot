package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "audit_logs",
        indexes = {
            @Index(name = "idx_audit_logs_target_id", columnList = "target_id"),
            @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
            @Index(name = "idx_audit_logs_actor_id", columnList = "actor_id")
        })
public class AuditLog extends ImmutableEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 50)
    private String targetType;

    private UUID targetId;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(length = 45)
    private String ipAddress;
}
