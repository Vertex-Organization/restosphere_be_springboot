package com.vertex.restosphere.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "areas")
public class Area extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean active = true;

    @ColumnDefault("0")
    @Column(nullable = false)
    private int sortOrder = 0;
}
