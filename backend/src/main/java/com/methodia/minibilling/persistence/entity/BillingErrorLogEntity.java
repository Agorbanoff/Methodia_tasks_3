package com.methodia.minibilling.persistence.entity;

import com.methodia.minibilling.model.ErrorSeverity;
import com.methodia.minibilling.model.ErrorStatus;
import com.methodia.minibilling.persistence.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "billing_error_logs")
public class BillingErrorLogEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Column(name = "type", length = 120, nullable = false)
    private String type;

    @Column(name = "description", length = 1000, nullable = false)
    private String description;

    @Column(name = "customer_id", length = 100)
    private String customerId;

    @Column(name = "module", length = 120, nullable = false)
    private String module;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20, nullable = false)
    private ErrorSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ErrorStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public BillingErrorLogEntity(String type, String description, String customerId, String module,
                                 ErrorSeverity severity, ErrorStatus status, OffsetDateTime createdAt) {
        this.type = type;
        this.description = description;
        this.customerId = customerId;
        this.module = module;
        this.severity = severity;
        this.status = status;
        this.createdAt = createdAt;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = IdGenerator.generateId();
        }
    }
}
