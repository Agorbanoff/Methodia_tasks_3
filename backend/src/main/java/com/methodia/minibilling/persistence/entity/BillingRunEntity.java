package com.methodia.minibilling.persistence.entity;

import com.methodia.minibilling.model.billing.BillingRunStatus;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "billing_runs")
public class BillingRunEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private BillingRunStatus status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by_id")
    private UserEntity startedBy;

    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "processed_records", nullable = false)
    private int processedRecords;

    @Column(name = "failed_records", nullable = false)
    private int failedRecords;

    @Column(name = "warning_records", nullable = false)
    private int warningRecords;

    @Column(name = "frozen_tariff_version", length = 100, nullable = false)
    private String frozenTariffVersion;

    @Column(name = "reference", length = 100, nullable = false)
    private String reference;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public BillingRunEntity(LocalDate periodStart, LocalDate periodEnd, UserEntity startedBy,
                            String reference, OffsetDateTime now) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.startedBy = startedBy;
        this.reference = reference;
        this.status = BillingRunStatus.NOT_STARTED;
        this.startedAt = now;
        this.frozenTariffVersion = "PENDING";
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = IdGenerator.generateId();
        }
    }

}
