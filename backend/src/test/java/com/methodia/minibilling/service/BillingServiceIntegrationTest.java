package com.methodia.minibilling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.methodia.minibilling.config.BillingProperties;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.repository.ConsumerCsvReader;
import com.methodia.minibilling.repository.InvoiceFileRepository;
import com.methodia.minibilling.repository.PriceCsvReader;
import com.methodia.minibilling.repository.ReadingCsvReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class BillingServiceIntegrationTest {

    @TempDir
    private Path tempDir;

    private Path inputDirectory;
    private Path outputDirectory;
    private BillingService billingService;

    @BeforeEach
    void setUp() throws Exception {
        inputDirectory = tempDir.resolve("input");
        outputDirectory = tempDir.resolve("output");
        Files.createDirectories(inputDirectory);
        writeInputFiles();

        ObjectMapper objectMapper = objectMapper();
        InvoiceFileNameService fileNameService = new InvoiceFileNameService();
        InvoiceFileRepository fileRepository = new InvoiceFileRepository(
                new BillingProperties(inputDirectory.toString(), outputDirectory.toString())
        );
        billingService = new BillingService(
                fileRepository,
                new ConsumerCsvReader(),
                new ReadingCsvReader(),
                new PriceCsvReader(),
                new ConsumptionService(),
                new PricingService(),
                new InvoiceNumberService(objectMapper),
                new InvoiceStorageService(objectMapper, fileNameService),
                Clock.fixed(Instant.parse("2024-03-05T10:15:30Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void assignsUniqueGlobalNumbersBetweenConsumersAndStoresFiles() throws Exception {
        InvoiceGenerationResult result = billingService.generateInvoices(2024, 3);
        List<Invoice> invoices = result.invoices();

        assertThat(result.generatedCount()).isEqualTo(2);
        assertThat(invoices)
                .extracting(Invoice::documentNumber)
                .containsExactly("1000", "1001");
        assertThat(Files.exists(outputDirectory.resolve("Marko Boikov Tsvetkov-1").resolve("1000-март-24.json")))
                .isTrue();
        assertThat(Files.exists(outputDirectory.resolve("Ivana Petrova-2").resolve("1001-март-24.json")))
                .isTrue();
    }

    @Test
    void duplicateGenerationReturnsExistingInvoiceWithoutNewDocumentNumber() throws Exception {
        InvoiceGenerationResult first = billingService.generateInvoices(2024, 3);
        InvoiceGenerationResult second = billingService.generateInvoices(2024, 3);

        assertThat(first.generatedCount()).isEqualTo(2);
        assertThat(second.generatedCount()).isZero();
        assertThat(second.invoices())
                .extracting(Invoice::documentNumber)
                .containsExactlyElementsOf(first.invoices().stream().map(Invoice::documentNumber).toList());

        try (var files = Files.walk(outputDirectory)) {
            assertThat(files.filter(path -> path.getFileName().toString().endsWith(".json")).count())
                    .isEqualTo(2);
        }
    }

    @Test
    void parallelGenerationDoesNotIssueDuplicateNumbersForSameMonth() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> billingService.generateInvoices(2024, 3));
            var second = executor.submit(() -> billingService.generateInvoices(2024, 3));

            InvoiceGenerationResult firstResult = first.get();
            InvoiceGenerationResult secondResult = second.get();

            assertThat(firstResult.generatedCount() + secondResult.generatedCount()).isEqualTo(2);
            assertThat(firstResult.invoices())
                    .extracting(Invoice::documentNumber)
                    .containsExactly("1000", "1001");
            assertThat(secondResult.invoices())
                    .extracting(Invoice::documentNumber)
                    .containsExactly("1000", "1001");
        }

        try (var files = Files.walk(outputDirectory)) {
            assertThat(files.filter(path -> path.getFileName().toString().endsWith(".json")).count())
                    .isEqualTo(2);
        }
    }

    @Test
    void continuesAfterRestart() {
        billingService.generateInvoices(2024, 3);

        BillingService serviceAfterRestart = newServiceWithFreshNumberService();
        List<Invoice> aprilInvoices = serviceAfterRestart.generateInvoices(2024, 4).invoices();

        assertThat(aprilInvoices)
                .extracting(Invoice::documentNumber)
                .containsExactly("1002", "1003");
    }

    private BillingService newServiceWithFreshNumberService() {
        ObjectMapper objectMapper = objectMapper();
        InvoiceFileNameService fileNameService = new InvoiceFileNameService();
        return new BillingService(
                new InvoiceFileRepository(new BillingProperties(inputDirectory.toString(), outputDirectory.toString())),
                new ConsumerCsvReader(),
                new ReadingCsvReader(),
                new PriceCsvReader(),
                new ConsumptionService(),
                new PricingService(),
                new InvoiceNumberService(objectMapper),
                new InvoiceStorageService(objectMapper, fileNameService),
                Clock.fixed(Instant.parse("2024-04-05T10:15:30Z"), ZoneOffset.UTC)
        );
    }

    private void writeInputFiles() throws Exception {
        Files.writeString(inputDirectory.resolve("users.csv"), """
                Marko Boikov Tsvetkov,1,1
                Ivana Petrova,2,1
                """, StandardCharsets.UTF_8);
        Files.writeString(inputDirectory.resolve("readings.csv"), """
                1,gas,2024-03-01T10:00:00+02:00,10
                1,gas,2024-03-31T10:00:00+02:00,15
                1,gas,2024-04-30T10:00:00+03:00,18
                2,gas,2024-03-01T10:00:00+02:00,20
                2,gas,2024-03-31T10:00:00+02:00,27
                2,gas,2024-04-30T10:00:00+03:00,30
                """, StandardCharsets.UTF_8);
        Files.writeString(inputDirectory.resolve("prices-1.csv"), """
                gas,2024-03-01,2024-03-31,2.00
                gas,2024-04-01,2024-04-30,2.00
                """, StandardCharsets.UTF_8);
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
