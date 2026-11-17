package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Getter
@Setter
@Entity
@Table(name = "shifts")
@Check(constraints = "ends_at > starts_at and minimum_staff >= 0")
public class Shift extends BaseEntity {
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant endsAt;
    @Column(nullable = false)
    private int minimumStaff;
}
