package com.methodia.minibilling.dto;

import java.nio.file.Path;

public record InvoiceDownload(
        String fileName,
        Path path
) {
}

