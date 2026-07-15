package com.methodia.minibilling.controller.dto;

public record HealthResponse(
        String status,
        String inputDirectory,
        String outputDirectory
) {
}

