package com.methodia.minibilling.service;

import com.methodia.minibilling.model.Invoice;

import java.util.List;

public record InvoiceGenerationResult(
        List<Invoice> invoices,
        int generatedCount
) {
}

