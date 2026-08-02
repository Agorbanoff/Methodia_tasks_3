package com.methodia.minibilling.controller.dto.billing;

import com.methodia.minibilling.model.BillingRunStatus;

import java.time.OffsetDateTime;

public record BillingRunResponse(
        String id,
        String periodStart,
        String periodEnd,
        BillingRunStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String startedBy,
        int processedRecords,
        int failedRecords,
        int warningRecords,
        int totalRecords,
        String frozenTariffVersion
) {
}
