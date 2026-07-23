package com.methodia.minibilling.mapper;

import com.methodia.minibilling.controller.dto.InvoiceDetailResponse;
import com.methodia.minibilling.controller.dto.InvoiceLineResponse;
import com.methodia.minibilling.controller.dto.InvoiceSummaryResponse;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.InvoiceLine;

public final class InvoiceMapper {

    public static InvoiceSummaryResponse toSummary(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.documentNumber(),
                invoice.consumer(),
                invoice.reference(),
                invoice.totalAmount(),
                invoice.lines().size()
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
