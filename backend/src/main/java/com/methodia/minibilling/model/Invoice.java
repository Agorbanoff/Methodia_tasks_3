package com.methodia.minibilling.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Invoice(
        Instant documentDate,
        String documentNumber,
        String consumer,
        String reference,
        BigDecimal totalAmount,
        List<InvoiceLine> lines
) {
}

