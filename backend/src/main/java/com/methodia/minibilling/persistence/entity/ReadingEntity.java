package com.methodia.minibilling.persistence.entity;

import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.model.reading.ReadingSource;
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
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 30, nullable = false)
    private ReadingSource source = ReadingSource.IMPORTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_import_id")
    private FileImportEntity fileImport;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = IdGenerator.generateId();
        }
        if (source == null) {
            source = selfReported ? ReadingSource.SELF_REPORTED : ReadingSource.IMPORTED;
        }
    }
}
