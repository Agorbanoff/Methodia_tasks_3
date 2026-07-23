package com.methodia.minibilling.persistence.entity;

import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.persistence.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "readings")
public class ReadingEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "product", length = 20, nullable = false)
    private Product product;

    @Column(name = "date_time", nullable = false)
    private OffsetDateTime dateTime;

    @Column(name = "last_reading", nullable = false, precision = 19, scale = 3)
    private BigDecimal lastReading;

    @Column(name = "invoiced", nullable = false)
    private boolean invoiced;

    @Column(name = "self_reported", nullable = false)
    private boolean selfReported;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_import_id")
    private FileImportEntity fileImport;

    public ReadingEntity(UserEntity user, Product product, OffsetDateTime dateTime, BigDecimal lastReading) {
        this.user = user;
        this.product = product;
        this.dateTime = dateTime;
        this.lastReading = lastReading;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = IdGenerator.generateId();
        }
    }
}
