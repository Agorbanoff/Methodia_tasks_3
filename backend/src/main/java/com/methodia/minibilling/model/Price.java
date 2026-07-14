package com.methodia.minibilling.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Price(
        String product,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal value,
        int priceListNumber
) {
}

