package com.methodia.minibilling.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceDetailResponse(
        Instant documentDate,
        String documentNumber,
        String consumer,
        String reference,
        BigDecimal totalAmount,
        List<InvoiceLineResponse> lines
) {
}

