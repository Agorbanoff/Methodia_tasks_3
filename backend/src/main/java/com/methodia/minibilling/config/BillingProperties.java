package com.methodia.minibilling.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "billing")
public record BillingProperties(
        @NotBlank String inputDirectory,
        @NotBlank String outputDirectory
) {
}

