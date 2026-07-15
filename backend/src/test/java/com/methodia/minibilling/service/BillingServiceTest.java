package com.methodia.minibilling.service;

import com.methodia.minibilling.controller.dto.HealthResponse;
import com.methodia.minibilling.model.Consumer;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.Price;
import com.methodia.minibilling.model.Reading;
import com.methodia.minibilling.repository.ConsumerCsvReader;
import com.methodia.minibilling.repository.InvoiceFileRepository;
import com.methodia.minibilling.repository.PriceCsvReader;
import com.methodia.minibilling.repository.ReadingCsvReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private InvoiceFileRepository invoiceFileRepository;
    @Mock
    private ConsumerCsvReader consumerCsvReader;
    @Mock
    private ReadingCsvReader readingCsvReader;
    @Mock
    private PriceCsvReader priceCsvReader;
    @Mock
    private InvoiceNumberService invoiceNumberService;
    @Mock
    private InvoiceStorageService invoiceStorageService;

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(
                invoiceFileRepository,
                consumerCsvReader,
                readingCsvReader,
                priceCsvReader,
                new ConsumptionService(),
                new PricingService(),
                invoiceNumberService,
                invoiceStorageService,
                Clock.fixed(Instant.parse("2024-03-05T10:15:30Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void getHealthReturnsConfiguredDirectories() {
        Path inputDirectory = Path.of("..", "data", "input").normalize();
        Path outputDirectory = Path.of("..", "data", "output").normalize();
        when(invoiceFileRepository.inputDirectory()).thenReturn(inputDirectory);
        when(invoiceFileRepository.outputDirectory()).thenReturn(outputDirectory);

        HealthResponse response = billingService.getHealth();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.inputDirectory()).isEqualTo(inputDirectory.toString());
        assertThat(response.outputDirectory()).isEqualTo(outputDirectory.toString());
    }

    @Test
    void generateInvoicesCalculatesAmountWithRoundingUp() {
        Path inputDirectory = Path.of("data", "input");
        Path usersFile = inputDirectory.resolve("users.csv");
        Path readingsFile = inputDirectory.resolve("readings.csv");
        mockInputFiles(inputDirectory, usersFile, readingsFile);

        Consumer consumer = new Consumer("Alice", "REF-1", 1);
        when(consumerCsvReader.read(usersFile)).thenReturn(List.of(consumer));
        when(readingCsvReader.read(readingsFile)).thenReturn(List.of(
                reading("REF-1", "gas", "2024-01-01T11:00:00+03:00", "10.000"),
                reading("REF-1", "gas", "2024-01-31T11:00:00+03:00", "11.001")
        ));
        when(priceCsvReader.readAll(inputDirectory)).thenReturn(List.of(
                price("gas", "2024-01-01", "2024-01-31", "0.333", 1)
        ));
        when(invoiceStorageService.findExistingInvoice(inputDirectory.resolve("out"), "REF-1", java.time.YearMonth.of(2024, 1)))
                .thenReturn(Optional.empty());
        when(invoiceNumberService.reserveDocumentNumbers(inputDirectory.resolve("out"), 1))
                .thenReturn(List.of("1000"));

        InvoiceGenerationResult result = billingService.generateInvoices(2024, 1);
        List<Invoice> invoices = result.invoices();

        assertThat(result.generatedCount()).isEqualTo(1);
        assertThat(invoices).hasSize(1);
        assertThat(invoices.getFirst().lines().getFirst().quantity()).isEqualByComparingTo("1.001");
        assertThat(invoices.getFirst().lines().getFirst().amount()).isEqualByComparingTo("0.34");
        assertThat(invoices.getFirst().totalAmount()).isEqualByComparingTo("0.34");
        assertThat(invoices.getFirst().documentNumber()).isEqualTo("1000");

        verify(invoiceStorageService).saveAll(eq(inputDirectory.resolve("out")), any(), eq(java.time.YearMonth.of(2024, 1)));
    }

    @Test
    void generateInvoicesHandlesDifferentConsumersWithSameProductSeparately() {
        Path inputDirectory = Path.of("data", "input");
        Path usersFile = inputDirectory.resolve("users.csv");
        Path readingsFile = inputDirectory.resolve("readings.csv");
        mockInputFiles(inputDirectory, usersFile, readingsFile);

        when(consumerCsvReader.read(usersFile)).thenReturn(List.of(
                new Consumer("Alice", "REF-1", 1),
                new Consumer("Bob", "REF-2", 1)
        ));
        when(readingCsvReader.read(readingsFile)).thenReturn(List.of(
                reading("REF-1", "gas", "2024-01-01T11:00:00+03:00", "10"),
                reading("REF-1", "gas", "2024-01-31T11:00:00+03:00", "15"),
                reading("REF-2", "gas", "2024-01-01T11:00:00+03:00", "20"),
                reading("REF-2", "gas", "2024-01-31T11:00:00+03:00", "27")
        ));
        when(priceCsvReader.readAll(inputDirectory)).thenReturn(List.of(
                price("gas", "2024-01-01", "2024-01-31", "2.00", 1)
        ));
        when(invoiceStorageService.findExistingInvoice(inputDirectory.resolve("out"), "REF-1", java.time.YearMonth.of(2024, 1)))
                .thenReturn(Optional.empty());
        when(invoiceStorageService.findExistingInvoice(inputDirectory.resolve("out"), "REF-2", java.time.YearMonth.of(2024, 1)))
                .thenReturn(Optional.empty());
        when(invoiceNumberService.reserveDocumentNumbers(inputDirectory.resolve("out"), 2))
                .thenReturn(List.of("1000", "1001"));

        InvoiceGenerationResult result = billingService.generateInvoices(2024, 1);
        List<Invoice> invoices = result.invoices();

        assertThat(result.generatedCount()).isEqualTo(2);
        assertThat(invoices)
                .extracting(Invoice::reference)
                .containsExactly("REF-1", "REF-2");
        assertThat(invoices)
                .extracting(Invoice::totalAmount)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("14.00"));
    }

    private void mockInputFiles(Path inputDirectory, Path usersFile, Path readingsFile) {
        when(invoiceFileRepository.inputDirectory()).thenReturn(inputDirectory);
        when(invoiceFileRepository.outputDirectory()).thenReturn(inputDirectory.resolve("out"));
        when(invoiceFileRepository.consumersFile()).thenReturn(usersFile);
        when(invoiceFileRepository.readingsFile()).thenReturn(readingsFile);
    }

    private Reading reading(String reference, String product, String dateTime, String value) {
        return new Reading(reference, product, OffsetDateTime.parse(dateTime), new BigDecimal(value));
    }

    private Price price(String product, String startDate, String endDate, String value, int priceListNumber) {
        return new Price(product, LocalDate.parse(startDate), LocalDate.parse(endDate), new BigDecimal(value), priceListNumber);
    }
}
