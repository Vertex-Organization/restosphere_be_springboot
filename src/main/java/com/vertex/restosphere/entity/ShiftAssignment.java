package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "shift_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"shift_id", "employee_id"}),
        indexes = @Index(name = "idx_shift_assignments_employee_id", columnList = "employee_id"))
public class ShiftAssignment extends ImmutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
}
