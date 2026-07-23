package com.methodia.minibilling.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.methodia.minibilling.export.InvoiceDownload;
import com.methodia.minibilling.exception.InvoiceNotFoundException;
import com.methodia.minibilling.mapper.InvoiceEntityMapper;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import com.methodia.minibilling.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceQueryService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceEntityMapper invoiceEntityMapper;
    private final ObjectMapper objectMapper;

    public InvoiceQueryService(
            InvoiceRepository invoiceRepository,
            InvoiceEntityMapper invoiceEntityMapper,
            ObjectMapper objectMapper
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceEntityMapper = invoiceEntityMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Invoice> findAll(Optional<Integer> year, Optional<Integer> month) {
        Optional<YearMonth> invoiceMonth = toYearMonth(year, month);
        List<InvoiceEntity> invoices = invoiceMonth
                .map(monthValue -> invoiceRepository.findByBillingYearAndBillingMonthOrderByNumberAsc(
                        monthValue.getYear(),
                        monthValue.getMonthValue()
                ))
                .orElseGet(invoiceRepository::findAllByOrderByNumberAsc);
        return invoices.stream()
                .map(invoiceEntityMapper::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public Invoice findByDocumentNumber(String documentNumber) {
        return invoiceRepository.findByNumber(documentNumber)
                .map(invoiceEntityMapper::toModel)
                .orElseThrow(() -> new InvoiceNotFoundException(documentNumber));
    }

    @Transactional(readOnly = true)
    public InvoiceDownload download(String documentNumber) {
        InvoiceEntity invoiceEntity = invoiceRepository.findByNumber(documentNumber)
                .orElseThrow(() -> new InvoiceNotFoundException(documentNumber));
        Invoice invoice = invoiceEntityMapper.toModel(invoiceEntity);
        String fileName = invoice.documentNumber() + ".json";
        try {
            return new InvoiceDownload(fileName, objectMapper.writeValueAsBytes(invoice));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize invoice %s".formatted(documentNumber), exception);
        }
    }

    private Optional<YearMonth> toYearMonth(Optional<Integer> year, Optional<Integer> month) {
        if (year.isEmpty() && month.isEmpty()) {
            return Optional.empty();
        }
        if (year.isEmpty() || month.isEmpty()) {
            throw new IllegalArgumentException("Both year and month must be provided when filtering invoices");
        }
        return Optional.of(YearMonth.of(year.get(), month.get()));
    }
}
