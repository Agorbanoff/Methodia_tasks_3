package com.methodia.minibilling.service.billing;

import java.math.BigDecimal;
import java.time.Instant;

public record ConsumptionPeriod(
        String product,
        BigDecimal quantity,
        Instant lineStart,
        Instant lineEnd
) {
}

