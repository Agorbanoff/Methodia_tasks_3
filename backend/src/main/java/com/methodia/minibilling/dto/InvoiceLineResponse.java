package com.methodia.minibilling.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceLineResponse(
        int index,
        BigDecimal quantity,
        Instant lineStart,
        Instant lineEnd,
        String product,
        BigDecimal price,
        int priceList,
        BigDecimal amount
) {
}

