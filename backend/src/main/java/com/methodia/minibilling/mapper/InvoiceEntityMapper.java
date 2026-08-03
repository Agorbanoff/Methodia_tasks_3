package com.methodia.minibilling.mapper;

import com.methodia.minibilling.model.invoice.Invoice;
import com.methodia.minibilling.model.invoice.InvoiceLine;
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
                invoice.getLines().stream()
                        .map(this::toModel)
                        .toList()
        );
    }

    private InvoiceLine toModel(InvoiceLineEntity line) {
        return new InvoiceLine(
                line.getLineId(),
                line.getQuantity(),
                line.getStartDateTime().toInstant(),
                line.getEndDateTime().toInstant(),
                line.getProduct() == com.methodia.minibilling.model.tariff.Product.ELECT ? "elec" : "gas",
                line.getPrice(),
                line.getPriceList(),
                line.getAmount()
        );
    }
}
