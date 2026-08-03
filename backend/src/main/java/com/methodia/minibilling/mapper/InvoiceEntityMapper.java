package com.methodia.minibilling.mapper;

import com.methodia.minibilling.model.invoice.Invoice;
import com.methodia.minibilling.model.invoice.InvoiceLine;
import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import com.methodia.minibilling.persistence.entity.InvoiceLineEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class InvoiceEntityMapper {

    public Invoice toModel(InvoiceEntity invoice) {
        YearMonth invoiceMonth = YearMonth.of(invoice.getBillingYear(), invoice.getBillingMonth());
        LocalDate periodStart = invoiceMonth.atDay(1);
        LocalDate periodEnd = invoiceMonth.atEndOfMonth();
        return new Invoice(
                invoice.getId(),
                invoice.getDateTime().toInstant(),
                invoice.getNumber(),
                invoice.getCustomer().getName(),
                invoice.getCustomer().getReference(),
                periodStart.toString(),
                periodEnd.toString(),
                invoice.getTotalAmount(),
                invoice.getTotalAmountWithVat(),
                invoice.getLines().stream()
                        .map(this::toModel)
                        .toList()
        );
    }

    private InvoiceLine toModel(InvoiceLineEntity line) {
        return new InvoiceLine(
                line.getLineId(),
                line.getSourceLine() == null ? null : java.util.List.of(line.getSourceLine().getLineId()),
                chargeName(line.getProduct()),
                line.getQuantity(),
                line.getStartDateTime().toInstant(),
                line.getEndDateTime().toInstant(),
                responseProduct(line.getProduct()),
                unit(line.getProduct()),
                line.getPrice(),
                line.getPriceList(),
                line.getAmount()
        );
    }

    private String responseProduct(Product product) {
        return switch (product) {
            case GAS -> "gas";
            case ELECT -> "elec";
            case STANDING_CHARGE, CCL -> null;
        };
    }

    private String chargeName(Product product) {
        return switch (product) {
            case STANDING_CHARGE -> "Standing charge";
            case CCL -> "CCL";
            case GAS, ELECT -> null;
        };
    }

    private String unit(Product product) {
        return product == Product.STANDING_CHARGE ? "days" : "kW/h";
    }
}
