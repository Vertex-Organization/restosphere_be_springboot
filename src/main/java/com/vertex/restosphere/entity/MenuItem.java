package com.vertex.restosphere.entity;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.common.enums.PriceUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "menu_items",
        indexes = @Index(name = "idx_menu_items_category_id", columnList = "category_id"))
public class MenuItem extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private MenuCategory category;

    // VND, khong co phan le.
    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    @Column(length = 1000)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "price_unit",
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) default 'portion'")
    private PriceUnit priceUnit = PriceUnit.PORTION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuItemStatus status = MenuItemStatus.AVAILABLE;
}
