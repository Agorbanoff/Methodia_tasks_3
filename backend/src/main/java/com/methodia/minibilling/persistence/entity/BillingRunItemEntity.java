package com.methodia.minibilling.persistence.entity;

import com.methodia.minibilling.model.BillingRunItemStatus;
import com.methodia.minibilling.model.ErrorSeverity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "billing_run_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_billing_run_items_run_customer",
                columnNames = {"billing_run_id", "customer_id"}
        )
)
public class BillingRunItemEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "billing_run_id", nullable = false)
    private BillingRunEntity billingRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private BillingRunItemStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private InvoiceEntity invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private ErrorSeverity severity;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "tariff_snapshot", columnDefinition = "text", nullable = false)
    private String tariffSnapshot;

    public BillingRunItemEntity(BillingRunEntity billingRun, CustomerEntity customer, String tariffSnapshot) {
        this.billingRun = billingRun;
        this.customer = customer;
        this.status = BillingRunItemStatus.PENDING;
        this.tariffSnapshot = tariffSnapshot;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = IdGenerator.generateId();
        }
    }
}
