package com.vertex.restosphere.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Thành viên nhà hàng; liên kết với lịch đặt bàn qua số điện thoại đã chuẩn hóa. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 30)
    private String phone;

    @Column(length = 254)
    private String email;

    private LocalDate birthday;

    @Column(nullable = false)
    private boolean marketingConsent;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private boolean enabled = true;
}
