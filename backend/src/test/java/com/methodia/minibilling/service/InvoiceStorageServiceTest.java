package com.methodia.minibilling.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.InvoiceLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceStorageServiceTest {

    @TempDir
    private Path tempDir;

    private ObjectMapper objectMapper;
    private InvoiceFileNameService fileNameService;
    private InvoiceStorageService storageService;

    @BeforeEach
    void setUp() {
        objectMapper = objectMapper();
        fileNameService = new InvoiceFileNameService();
        storageService = new InvoiceStorageService(objectMapper, fileNameService);
    }

    @Test
    void buildsFolderNameWithoutChangingNormalSpacesOrLetters() {
        assertThat(fileNameService.directoryName("Marko Boikov Tsvetkov", "1"))
                .isEqualTo("Marko Boikov Tsvetkov-1");
    }

    @Test
    void usesBulgarianMonthInFileName() {
        assertThat(fileNameService.fileName("1000", YearMonth.of(2024, 3)))
                .isEqualTo("1000-март-24.json");
    }

    @Test
    void writesPrettyPrintedUtf8JsonWithUtcDatesAndNumericAmounts() throws Exception {
        Invoice invoice = invoice("1000", "Марко Boikov Tsvetkov", "1");

        storageService.saveAll(tempDir, List.of(invoice), YearMonth.of(2024, 3));

        Path file = tempDir.resolve("Марко Boikov Tsvetkov-1").resolve("1000-март-24.json");
        assertThat(Files.exists(file)).isTrue();

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(json).contains("\n");
        assertThat(json).contains("\"documentDate\" : \"2024-03-05T10:15:30Z\"");
        assertThat(json).contains("\"totalAmount\" : 3.34");
        assertThat(json).contains("Марко");

        JsonNode root = objectMapper.readTree(file.toFile());
        assertThat(root.get("totalAmount").isNumber()).isTrue();
        assertThat(root.get("lines").get(0).get("amount").isNumber()).isTrue();
    }

    @Test
    void findsExistingInvoiceForReferenceYearAndMonth() {
        Invoice invoice = invoice("1000", "Alice", "REF-1");
        storageService.saveAll(tempDir, List.of(invoice), YearMonth.of(2024, 3));

        assertThat(storageService.findExistingInvoice(tempDir, "REF-1", YearMonth.of(2024, 3)))
                .isPresent()
                .get()
                .extracting(Invoice::documentNumber)
                .isEqualTo("1000");
        assertThat(storageService.findExistingInvoice(tempDir, "REF-1", YearMonth.of(2024, 4)))
                .isEmpty();
    }

    private Invoice invoice(String documentNumber, String consumer, String reference) {
        return new Invoice(
                Instant.parse("2024-03-05T10:15:30Z"),
                documentNumber,
                consumer,
                reference,
                new BigDecimal("3.34"),
                List.of(new InvoiceLine(
                        1,
                        new BigDecimal("1.001"),
                        Instant.parse("2024-03-01T08:00:00Z"),
                        Instant.parse("2024-03-31T08:00:00Z"),
                        "gas",
                        new BigDecimal("3.333"),
                        1,
                        new BigDecimal("3.34")
                ))
        );
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

