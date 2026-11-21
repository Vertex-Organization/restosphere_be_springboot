package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Getter
@Setter
@Entity
@Table(
        name = "order_items",
        indexes = {
            @Index(name = "idx_order_items_order_id", columnList = "order_id"),
            @Index(name = "idx_order_items_menu_item_id", columnList = "menu_item_id"),
            @Index(name = "idx_order_items_status", columnList = "status")
        })
@Check(constraints = "quantity > 0 and unit_price >= 0")
public class OrderItem extends BaseEntity {
    public enum Status {
        SUBMITTED,
        PREPARING,
        READY,
        SERVED,
        REJECTED,
        CANCELLED,
        VOIDED
    }

    @Version private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false, length = 150)
    private String itemName;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chef_id")
    private User chef;

    private Instant startedAt;
    private Instant readyAt;
    private Instant servedAt;

    @Column(length = 1000)
    private String note;

    @Column(length = 1000)
    private String rejectionReason;
}
