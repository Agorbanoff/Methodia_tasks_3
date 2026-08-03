package com.methodia.minibilling.model.reading;

import com.methodia.minibilling.model.tariff.Product;

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
