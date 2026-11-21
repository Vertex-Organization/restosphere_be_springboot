package com.vertex.restosphere.entity;

import com.vertex.restosphere.common.enums.MenuGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "menu_categories")
public class MenuCategory extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "menu_group",
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) default 'food'")
    private MenuGroup menuGroup = MenuGroup.FOOD;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean visible = true;
}
