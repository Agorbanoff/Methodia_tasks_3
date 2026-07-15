package com.methodia.minibilling.controller.dto;

import java.nio.file.Path;

public record InvoiceDownload(
        String fileName,
        Path path
) {
}

