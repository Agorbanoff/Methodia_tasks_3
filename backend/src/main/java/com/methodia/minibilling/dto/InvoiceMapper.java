package com.methodia.minibilling.dto;

import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.InvoiceLine;

public final class InvoiceMapper {

    private InvoiceMapper() {
    }

    public static InvoiceSummaryResponse toSummary(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.documentNumber(),
                invoice.consumer(),
                invoice.reference(),
                invoice.documentDate(),
                invoice.totalAmount()
        );
    }

    public static InvoiceDetailResponse toDetail(Invoice invoice) {
        return new InvoiceDetailResponse(
                invoice.documentDate(),
                invoice.documentNumber(),
                invoice.consumer(),
                invoice.reference(),
                invoice.totalAmount(),
                invoice.lines().stream().map(InvoiceMapper::toLine).toList()
        );
    }

    private static InvoiceLineResponse toLine(InvoiceLine line) {
        return new InvoiceLineResponse(
                line.index(),
                line.quantity(),
                line.lineStart(),
                line.lineEnd(),
                line.product(),
                line.price(),
                line.priceList(),
                line.amount()
        );
    }
}
