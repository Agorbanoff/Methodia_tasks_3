package com.methodia.minibilling.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

record ScenarioExpectedLine(
        OffsetDateTime startDateTime,
        OffsetDateTime endDateTime,
        BigDecimal quantity,
        BigDecimal price
) {
}
