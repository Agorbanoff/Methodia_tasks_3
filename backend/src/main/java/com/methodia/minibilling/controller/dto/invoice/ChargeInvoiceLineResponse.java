package com.methodia.minibilling.controller.dto.invoice;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ChargeInvoiceLineResponse(
        int index,
        List<Integer> lines,
        String name,
        BigDecimal quantity,
        @JsonProperty("start")
        Instant start,
        @JsonProperty("end")
        Instant end,
        String unit,
        BigDecimal price,
        BigDecimal amount,
        Instant lineStart,
        Instant lineEnd
) implements InvoiceLineResponse {
}
