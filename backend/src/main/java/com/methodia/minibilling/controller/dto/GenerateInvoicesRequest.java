package com.methodia.minibilling.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GenerateInvoicesRequest(
        @Min(1900) @Max(2100) int year,
        @Min(1) @Max(12) int month
) {
}
