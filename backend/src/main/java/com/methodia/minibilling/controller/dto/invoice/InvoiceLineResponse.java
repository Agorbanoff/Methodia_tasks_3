package com.methodia.minibilling.controller.dto.invoice;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceLineResponse(
        int index,
        String product,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        Instant lineStart,
        Instant lineEnd,
        String priceList
) {
}
