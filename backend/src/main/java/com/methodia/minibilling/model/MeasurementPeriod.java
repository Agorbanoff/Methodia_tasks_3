package com.methodia.minibilling.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MeasurementPeriod(
        OffsetDateTime startDateTime,
        OffsetDateTime endDateTime,
        BigDecimal quantity,
        Product product,
        int priceList
) {
}
