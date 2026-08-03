package com.methodia.minibilling.controller.dto.report;

import com.methodia.minibilling.controller.dto.billing.BillingRunResponse;
import com.methodia.minibilling.model.billing.BillingRunStatus;

public record BillingReportResponse(
        String runId,
        BillingRunStatus status,
        int processedRecords,
        int successfulInvoices,
        int failedRecords,
        int warningRecords,
        long duration,
        String failureSummary,
        BillingRunResponse run
) {
}
