package com.methodia.minibilling.controller.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceSummaryResponse(
        String documentNumber,
        String consumer,
        String reference,
        Instant documentDate,
        BigDecimal totalAmount
) {
}
