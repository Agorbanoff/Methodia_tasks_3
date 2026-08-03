package com.methodia.minibilling.model.tariff;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record QuantityPricePeriod(
        OffsetDateTime startDateTime,
        OffsetDateTime endDateTime,
        BigDecimal quantity,
        Product product,
        BigDecimal price,
        int priceList
) {
}
