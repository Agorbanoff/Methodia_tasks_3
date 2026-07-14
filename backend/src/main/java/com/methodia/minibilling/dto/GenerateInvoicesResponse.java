package com.methodia.minibilling.dto;

import java.util.List;

public record GenerateInvoicesResponse(
        int year,
        int month,
        int generatedCount,
        List<InvoiceSummaryResponse> invoices
) {
}

