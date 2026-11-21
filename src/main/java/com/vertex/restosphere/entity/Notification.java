package com.vertex.restosphere.entity;

import com.vertex.restosphere.common.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notifications")
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
    private User recipient;
}
