package com.vertex.restosphere.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "restaurant_settings")
@Check(
        constraints =
                "singleton_key = 1 and hold_minutes >= 0 and no_show_minutes >= 0 and vat_rate"
                        + " between 0 and 100 and service_charge_rate between 0 and 100")
public class RestaurantSettings extends BaseEntity {
    private boolean onlineReservations = true;
    private int bufferMinutes = 15;
    private String timezone = "Asia/Ho_Chi_Minh";
    private String currency = "VND";
    private String email;
    private String description;
    private String openingHours;
    private String facebook;
    private String instagram;
    private String tiktok;
    private String logoUrl;
    private String coverUrl;
    private boolean emailNotifications;
    private long revision;

    @Column(nullable = false, unique = true)
    private int singletonKey = 1;

    @Column(nullable = false, length = 150)
    private String name;

    private String address;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private int holdMinutes = 15;

    @Column(nullable = false)
    private int noShowMinutes = 30;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceChargeRate = BigDecimal.ZERO;

    private String bankName;
    private String bankAccountNumber;
    private String bankAccountName;

    @ColumnDefault("10")
    @Column(nullable = false)
    private int cashierDiscountPercent = 10;

    @ColumnDefault("10000000")
    @Column(nullable = false)
    private long managerApprovalLimit = 10_000_000L;
}
