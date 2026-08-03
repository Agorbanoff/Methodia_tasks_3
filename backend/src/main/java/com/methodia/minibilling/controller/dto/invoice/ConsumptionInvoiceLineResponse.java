package com.methodia.minibilling.controller.dto.invoice;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record ConsumptionInvoiceLineResponse(
        int index,
        BigDecimal quantity,
        @JsonProperty("start")
        Instant start,
        @JsonProperty("end")
        Instant end,
        String product,
        String unit,
        BigDecimal price,
        int priceList,
        BigDecimal amount,
        Instant lineStart,
        Instant lineEnd
) implements InvoiceLineResponse {
}
