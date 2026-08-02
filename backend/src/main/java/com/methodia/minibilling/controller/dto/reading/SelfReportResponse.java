package com.methodia.minibilling.controller.dto.reading;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SelfReportResponse(
        String id,
        String reference,
        String consumer,
        LocalDate date,
        String service,
        BigDecimal amount,
        String status,
        OffsetDateTime requestedAt,
        OffsetDateTime reviewedAt,
        String reviewedBy
) {
}
