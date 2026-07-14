package com.methodia.minibilling.dto;

public record HealthResponse(
        String status,
        String inputDirectory,
        String outputDirectory
) {
}

