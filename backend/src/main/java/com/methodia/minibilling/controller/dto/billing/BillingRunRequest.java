package com.methodia.minibilling.controller.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BillingRunRequest(
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String startDate,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String endDate,
        @NotBlank String reference
) {
}
