package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class OrderDraft extends BaseEntity {
    @ManyToOne(optional = false) private TableSession session;
    @ManyToOne(optional = false) private User waiter;
    @ManyToOne(optional = false) private MenuItem menuItem;
    @Column(nullable = false, precision = 10, scale = 3) private BigDecimal quantity = BigDecimal.ONE;
    private String note;
}
