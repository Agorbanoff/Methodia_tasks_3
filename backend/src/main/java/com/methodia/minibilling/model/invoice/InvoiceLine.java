package com.methodia.minibilling.model.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceLine(
        int index,
        List<Integer> lines,
        String name,
        BigDecimal quantity,
        Instant lineStart,
        Instant lineEnd,
        String product,
        String unit,
        BigDecimal price,
        int priceList,
        BigDecimal amount
) {
}

