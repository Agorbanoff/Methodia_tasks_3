package com.methodia.minibilling.model.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Invoice(
        String id,
        Instant documentDate,
        String documentNumber,
        String consumer,
        String reference,
        String periodStart,
        String periodEnd,
        BigDecimal totalAmount,
        BigDecimal totalAmountWithVat,
        List<InvoiceLine> lines
) {

    public Invoice(Instant documentDate, String documentNumber, String consumer, String reference,
                   BigDecimal totalAmount, List<InvoiceLine> lines) {
        this(null, documentDate, documentNumber, consumer, reference, null, null, totalAmount, totalAmount, lines);
    }
}
