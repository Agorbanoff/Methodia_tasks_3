package com.methodia.minibilling.model.invoice;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceLine(
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

