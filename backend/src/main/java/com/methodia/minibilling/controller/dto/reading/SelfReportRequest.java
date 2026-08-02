package com.methodia.minibilling.controller.dto.reading;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SelfReportRequest(
        @NotNull LocalDate date,
        @NotBlank String service,
        @NotNull @DecimalMin(value = "0.0") BigDecimal amount
) {
}
