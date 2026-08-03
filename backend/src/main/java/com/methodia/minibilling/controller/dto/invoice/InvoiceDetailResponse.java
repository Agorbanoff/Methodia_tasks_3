package com.methodia.minibilling.controller.dto.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceDetailResponse(
        String documentNumber,
        Instant documentDate,
        String consumer,
        String reference,
        String periodStart,
        String periodEnd,
        BigDecimal totalAmount,
        BigDecimal totalAmountWithVat,
        int linesCount,
        List<InvoiceLineResponse> lines,
        List<VatLineResponse> vat
) {
}
