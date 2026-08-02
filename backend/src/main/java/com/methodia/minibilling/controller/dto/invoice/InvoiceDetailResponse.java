package com.methodia.minibilling.controller.dto.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceDetailResponse(
        String id,
        String documentNumber,
        Instant documentDate,
        String consumer,
        String reference,
        String periodStart,
        String periodEnd,
        BigDecimal totalAmount,
        int linesCount,
        List<InvoiceLineResponse> lines
) {
}
