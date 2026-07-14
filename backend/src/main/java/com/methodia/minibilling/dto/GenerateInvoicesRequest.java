package com.methodia.minibilling.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GenerateInvoicesRequest(
        @Min(1900) int year,
        @Min(1) @Max(12) int month
) {
}

