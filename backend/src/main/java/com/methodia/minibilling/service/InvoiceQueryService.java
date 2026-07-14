package com.methodia.minibilling.service;

import com.methodia.minibilling.dto.InvoiceDownload;
import com.methodia.minibilling.exception.InvoiceNotFoundException;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.repository.InvoiceFileRepository;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceQueryService {

    private final InvoiceFileRepository invoiceFileRepository;
    private final InvoiceStorageService invoiceStorageService;

    public InvoiceQueryService(InvoiceFileRepository invoiceFileRepository, InvoiceStorageService invoiceStorageService) {
        this.invoiceFileRepository = invoiceFileRepository;
        this.invoiceStorageService = invoiceStorageService;
    }

    public List<Invoice> findAll(Optional<Integer> year, Optional<Integer> month) {
        Optional<YearMonth> invoiceMonth = toYearMonth(year, month);
        return invoiceStorageService.findAll(invoiceFileRepository.outputDirectory(), invoiceMonth);
    }

    public Invoice findByDocumentNumber(String documentNumber) {
        return invoiceStorageService.findByDocumentNumber(invoiceFileRepository.outputDirectory(), documentNumber)
                .orElseThrow(() -> new InvoiceNotFoundException(documentNumber));
    }

    public InvoiceDownload download(String documentNumber) {
        return invoiceStorageService.findInvoiceFile(invoiceFileRepository.outputDirectory(), documentNumber)
                .map(path -> new InvoiceDownload(path.getFileName().toString(), path))
                .orElseThrow(() -> new InvoiceNotFoundException(documentNumber));
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

