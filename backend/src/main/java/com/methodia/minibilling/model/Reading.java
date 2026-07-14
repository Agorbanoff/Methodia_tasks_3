package com.methodia.minibilling.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record Reading(
        String reference,
        String product,
        OffsetDateTime dateTime,
        BigDecimal value
) {
}

