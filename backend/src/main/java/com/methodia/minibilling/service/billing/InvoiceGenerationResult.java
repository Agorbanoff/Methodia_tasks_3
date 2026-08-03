package com.methodia.minibilling.service.billing;

import com.methodia.minibilling.model.invoice.Invoice;

import java.util.List;

public record InvoiceGenerationResult(
        List<Invoice> invoices,
        int generatedCount,
        int skippedExistingCount,
        List<String> warnings
) {

    public InvoiceGenerationResult(List<Invoice> invoices, int generatedCount) {
        this(invoices, generatedCount, 0, List.of());
    }

    public InvoiceGenerationResult(List<Invoice> invoices, int generatedCount, int skippedExistingCount) {
        this(invoices, generatedCount, skippedExistingCount, List.of());
    }
}
