package com.methodia.minibilling.mapper;

import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.InvoiceLine;
import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import com.methodia.minibilling.persistence.entity.InvoiceLineEntity;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class InvoiceEntityMapper {

    public Invoice toModel(InvoiceEntity invoice) {
        return new Invoice(
                invoice.getDateTime().toInstant(),
                invoice.getNumber(),
                invoice.getUser().getName(),
                invoice.getUser().getReference(),
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
                line.getProduct().name().toLowerCase(Locale.ROOT),
                line.getPrice(),
                line.getPriceList(),
                line.getAmount()
        );
    }
}
