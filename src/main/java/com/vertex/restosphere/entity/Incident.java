package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "incidents")
public class Incident extends BaseEntity {
    public enum Status { OPEN, ACKNOWLEDGED, RESOLVED, CLOSED }
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    @Column(nullable = false, length = 50)
    private String type;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;
    @Column(length = 2000)
    private String resolution;
    private Instant resolvedAt;
}
