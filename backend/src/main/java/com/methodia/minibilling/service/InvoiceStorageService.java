package com.methodia.minibilling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.methodia.minibilling.model.Invoice;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class InvoiceStorageService {

    private final ObjectMapper objectMapper;
    private final InvoiceFileNameService invoiceFileNameService;

    public InvoiceStorageService(ObjectMapper objectMapper, InvoiceFileNameService invoiceFileNameService) {
        this.objectMapper = objectMapper;
        this.invoiceFileNameService = invoiceFileNameService;
    }

    public Optional<Invoice> findExistingInvoice(Path outputDirectory, String reference, YearMonth invoiceMonth) {
        if (!Files.isDirectory(outputDirectory)) {
            return Optional.empty();
        }

        String expectedSuffix = "-%s-%s.json".formatted(
                invoiceFileNameService.bulgarianMonth(invoiceMonth),
                "%02d".formatted(invoiceMonth.getYear() % 100)
        );

        try (Stream<Path> files = Files.walk(outputDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(expectedSuffix))
                    .map(this::readInvoice)
                    .flatMap(Optional::stream)
                    .filter(invoice -> invoice.reference().equals(reference))
                    .findFirst();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan invoice output directory %s".formatted(outputDirectory), exception);
        }
    }

    public void saveAll(Path outputDirectory, List<Invoice> invoices, YearMonth invoiceMonth) {
        try {
            for (Invoice invoice : invoices) {
                Path invoicePath = invoiceFileNameService.invoicePath(outputDirectory, invoice, invoiceMonth);
                Files.createDirectories(invoicePath.getParent());
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(invoicePath.toFile(), invoice);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write invoice JSON files to %s".formatted(outputDirectory), exception);
        }
    }

    public List<Invoice> findAll(Path outputDirectory, Optional<YearMonth> invoiceMonth) {
        if (!Files.isDirectory(outputDirectory)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(outputDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> invoiceMonth.map(month -> matchesMonth(path, month)).orElse(true))
                    .map(this::readInvoice)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(Invoice::documentNumber))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan invoice output directory %s".formatted(outputDirectory), exception);
        }
    }

    public Optional<Invoice> findByDocumentNumber(Path outputDirectory, String documentNumber) {
        return findInvoiceFile(outputDirectory, documentNumber)
                .flatMap(this::readInvoice);
    }

    public Optional<Path> findInvoiceFile(Path outputDirectory, String documentNumber) {
        if (!Files.isDirectory(outputDirectory)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.walk(outputDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> readInvoice(path)
                            .map(invoice -> invoice.documentNumber().equals(documentNumber))
                            .orElse(false))
                    .findFirst();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan invoice output directory %s".formatted(outputDirectory), exception);
        }
    }

    private boolean matchesMonth(Path path, YearMonth invoiceMonth) {
        String expectedSuffix = "-%s-%s.json".formatted(
                invoiceFileNameService.bulgarianMonth(invoiceMonth),
                "%02d".formatted(invoiceMonth.getYear() % 100)
        );
        return path.getFileName().toString().endsWith(expectedSuffix);
    }

    private Optional<Invoice> readInvoice(Path file) {
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), Invoice.class));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
