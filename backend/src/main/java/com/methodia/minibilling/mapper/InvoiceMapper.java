package com.methodia.minibilling.mapper;

import com.methodia.minibilling.controller.dto.invoice.ChargeInvoiceLineResponse;
import com.methodia.minibilling.controller.dto.invoice.ConsumptionInvoiceLineResponse;
import com.methodia.minibilling.controller.dto.invoice.InvoiceDetailResponse;
import com.methodia.minibilling.controller.dto.invoice.InvoiceLineResponse;
import com.methodia.minibilling.controller.dto.invoice.VatLineResponse;
import com.methodia.minibilling.model.invoice.Invoice;
import com.methodia.minibilling.model.invoice.InvoiceLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class InvoiceMapper {

    private static final BigDecimal VAT_PERCENTAGE = new BigDecimal("20");

    public static InvoiceDetailResponse toDetail(Invoice invoice) {
        List<InvoiceLineResponse> lines = invoice.lines().stream().map(InvoiceMapper::toLine).toList();
        return new InvoiceDetailResponse(
                invoice.documentNumber(),
                invoice.documentDate(),
                invoice.consumer(),
                invoice.reference(),
                invoice.periodStart(),
                invoice.periodEnd(),
                invoice.totalAmount(),
                invoice.totalAmountWithVat(),
                lines.size(),
                lines,
                vatLines(invoice)
        );
    }

    private static List<VatLineResponse> vatLines(Invoice invoice) {
        if (invoice.lines().isEmpty()) {
            return List.of();
        }
        List<Integer> lineIndexes = invoice.lines().stream().map(InvoiceLine::index).toList();
        BigDecimal amount = invoice.totalAmountWithVat()
                .subtract(invoice.totalAmount())
                .setScale(2, RoundingMode.HALF_UP);
        return List.of(new VatLineResponse(1, lineIndexes, VAT_PERCENTAGE, amount));
    }

    private static InvoiceLineResponse toLine(InvoiceLine line) {
        if (line.name() != null) {
            return new ChargeInvoiceLineResponse(line.index(), line.lines(), line.name(), line.quantity(),
                    line.lineStart(), line.lineEnd(), line.unit(), line.price(), line.amount(),
                    line.lineStart(), line.lineEnd());
        }
        return new ConsumptionInvoiceLineResponse(line.index(), line.quantity(), line.lineStart(), line.lineEnd(),
                line.product(), line.unit(), line.price(), line.priceList(), line.amount(),
                line.lineStart(), line.lineEnd());
    }
}
