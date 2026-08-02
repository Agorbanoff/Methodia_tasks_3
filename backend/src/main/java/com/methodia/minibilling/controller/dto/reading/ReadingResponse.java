package com.methodia.minibilling.controller.dto.reading;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReadingResponse(
        String id,
        String reference,
        String consumer,
        OffsetDateTime dateTime,
        String product,
        BigDecimal lastReading,
        boolean selfReported,
        boolean invoiced
) {
}
