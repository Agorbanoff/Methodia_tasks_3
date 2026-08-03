package com.methodia.minibilling.controller.dto.invoice;

import java.math.BigDecimal;
import java.util.List;

public record VatLineResponse(
        int index,
        List<Integer> lines,
        BigDecimal percentage,
        BigDecimal amount
) {
}
