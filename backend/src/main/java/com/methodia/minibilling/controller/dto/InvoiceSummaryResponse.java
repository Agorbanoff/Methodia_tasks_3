package com.methodia.minibilling.controller.dto;

import java.math.BigDecimal;

public record InvoiceSummaryResponse(
        String documentNumber,
        String consumer,
        String reference,
        BigDecimal totalAmount,
        int linesCount
) {
}
