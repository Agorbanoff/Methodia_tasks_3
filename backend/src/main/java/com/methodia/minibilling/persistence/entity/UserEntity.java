package com.methodia.minibilling.persistence.entity;

import com.methodia.minibilling.persistence.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "reference", length = 100, nullable = false, unique = true)
    private String reference;

    @Column(name = "price_list", nullable = false)
    private int priceList;

    @Column(name = "tariff_code", length = 50)
    private String tariffCode;

    @Column(name = "username", length = 100, unique = true)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "role", length = 20, nullable = false)
    private String role = "USER";

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UserEntity(String id, String name, String reference, int priceList, List<ReadingEntity> readings) {
        this.id = id;
        this.name = name;
        this.reference = reference;
        this.priceList = priceList;
        this.tariffCode = "T" + priceList;
        this.username = reference;
        this.role = "USER";
    }

    public String customerReference() {
        return customer == null ? reference : customer.getReference();
    }

    public String displayName() {
        return customer == null ? name : customer.getName();
    }

    public int effectivePriceList() {
        return priceList;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = IdGenerator.generateId();
        }
        if (username == null || username.isBlank()) {
            username = reference;
        }
        if (role == null || role.isBlank()) {
            role = "USER";
        }
        if (tariffCode == null || tariffCode.isBlank()) {
            tariffCode = "T" + priceList;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
