package com.methodia.minibilling.mapper;

import com.methodia.minibilling.controller.dto.invoice.InvoiceDetailResponse;
import com.methodia.minibilling.controller.dto.invoice.InvoiceLineResponse;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.InvoiceLine;

public final class InvoiceMapper {

    public static InvoiceDetailResponse toDetail(Invoice invoice) {
        return new InvoiceDetailResponse(
                invoice.id(),
                invoice.documentNumber(),
                invoice.documentDate(),
                invoice.consumer(),
                invoice.reference(),
                invoice.periodStart(),
                invoice.periodEnd(),
                invoice.totalAmount(),
                invoice.lines().size(),
                invoice.lines().stream().map(InvoiceMapper::toLine).toList()
        );
    }

    private static InvoiceLineResponse toLine(InvoiceLine line) {
        return new InvoiceLineResponse(
                line.index(),
                line.product(),
                line.quantity(),
                line.price(),
                line.amount(),
                line.lineStart(),
                line.lineEnd(),
                "T" + line.priceList()
        );
    }
}
