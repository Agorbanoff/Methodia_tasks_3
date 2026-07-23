package com.methodia.minibilling.export;

public record InvoiceDownload(
        String fileName,
        byte[] content
) {
}
