package com.methodia.minibilling.persistence.entity;

import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.model.reading.SelfReportStatus;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "self_reports")
public class SelfReportEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "service", length = 20, nullable = false)
    private Product service;

    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private SelfReportStatus status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private UserEntity reviewedBy;

    public SelfReportEntity(CustomerEntity customer, Product service, LocalDate readingDate,
                            BigDecimal amount, OffsetDateTime requestedAt) {
        this.customer = customer;
        this.service = service;
        this.readingDate = readingDate;
        this.amount = amount;
        this.status = SelfReportStatus.PENDING;
        this.requestedAt = requestedAt;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = IdGenerator.generateId();
        }
    }
}
