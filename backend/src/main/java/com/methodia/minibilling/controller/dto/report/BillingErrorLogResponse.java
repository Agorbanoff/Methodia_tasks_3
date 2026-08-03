package com.methodia.minibilling.controller.dto.report;

import com.methodia.minibilling.model.error.ErrorSeverity;
import com.methodia.minibilling.model.error.ErrorStatus;

import java.time.OffsetDateTime;

public record BillingErrorLogResponse(
        OffsetDateTime occurredAt,
        String errorId,
        String errorType,
        String customerId,
        String module,
        ErrorSeverity severity,
        String description,
        ErrorStatus status
) {
}
