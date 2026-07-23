package com.methodia.minibilling.service;

import com.methodia.minibilling.exception.IncompletePriceCoverageException;
import com.methodia.minibilling.exception.NoImportedDataException;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.InvoiceLine;
import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.FileImportRepository;
import com.methodia.minibilling.repository.InvoiceLineRepository;
import com.methodia.minibilling.repository.InvoiceRepository;
import com.methodia.minibilling.repository.PriceRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class BillingDatabaseServiceTest {

    @Autowired
    private BillingService billingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLineRepository invoiceLineRepository;

    @Autowired
    private FileImportRepository fileImportRepository;

    @BeforeEach
    void setUp() {
        invoiceLineRepository.deleteAllInBatch();
        invoiceRepository.deleteAllInBatch();
        readingRepository.deleteAllInBatch();
        priceRepository.deleteAllInBatch();
        fileImportRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void consumerWithSinglePriceCreatesOneInvoiceLine() {
        UserEntity user = saveUser("Alice", "REF-1", 1);
        saveReading(user, Product.GAS, "2024-01-01T00:00:00+02:00", "10.00");
        saveReading(user, Product.GAS, "2024-01-31T23:59:59+02:00", "20.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "2.00", 1);

        Invoice invoice = billingService.generateInvoices(2024, 1).invoices().getFirst();

        assertThat(invoice.lines()).hasSize(1);
        assertThat(invoice.lines().getFirst().quantity()).isEqualByComparingTo("10.00");
        assertThat(invoice.lines().getFirst().price()).isEqualByComparingTo("2.00");
        assertThat(invoice.lines().getFirst().amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void consumerWithThreePricesCreatesThreeInvoiceLinesAndTotalAmountIsSum() {
        UserEntity user = saveUser("Alice", "REF-1", 1);
        saveReading(user, Product.ELECT, "2024-01-01T00:00:00+02:00", "0.00");
        saveReading(user, Product.ELECT, "2024-01-31T23:59:59+02:00", "31.00");
        savePrice(Product.ELECT, "2024-01-01", "2024-01-10", "1.00", 1);
        savePrice(Product.ELECT, "2024-01-11", "2024-01-20", "2.00", 1);
        savePrice(Product.ELECT, "2024-01-21", "2024-01-31", "3.00", 1);

        Invoice invoice = billingService.generateInvoices(2024, 1).invoices().getFirst();

        assertThat(invoice.lines()).hasSize(3);
        assertThat(invoice.lines()).extracting(InvoiceLine::index).containsExactly(1, 2, 3);
        assertThat(invoice.lines()).extracting(InvoiceLine::amount)
                .containsExactly(new BigDecimal("10.23"), new BigDecimal("20.46"), new BigDecimal("31.62"));
        assertThat(invoice.totalAmount()).isEqualByComparingTo("62.31");
    }

    @Test
    void usesPricesTwoWhenUserHasPriceListTwo() {
        UserEntity user = saveUser("Bob", "REF-2", 2);
        saveReading(user, Product.GAS, "2024-01-01T00:00:00+02:00", "5.00");
        saveReading(user, Product.GAS, "2024-01-31T23:59:59+02:00", "8.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "100.00", 1);
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "2.00", 2);

        Invoice invoice = billingService.generateInvoices(2024, 1).invoices().getFirst();

        assertThat(invoice.lines()).singleElement()
                .satisfies(line -> {
                    assertThat(line.priceList()).isEqualTo(2);
                    assertThat(line.price()).isEqualByComparingTo("2.00");
                    assertThat(line.amount()).isEqualByComparingTo("6.00");
                });
    }

    @Test
    void usesPricesOneWhenUserHasPriceListOne() {
        UserEntity user = saveUser("Alice", "REF-1", 1);
        saveReading(user, Product.GAS, "2024-01-01T00:00:00+02:00", "5.00");
        saveReading(user, Product.GAS, "2024-01-31T23:59:59+02:00", "8.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "3.00", 1);
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "100.00", 2);

        Invoice invoice = billingService.generateInvoices(2024, 1).invoices().getFirst();

        assertThat(invoice.lines()).singleElement()
                .satisfies(line -> {
                    assertThat(line.priceList()).isEqualTo(1);
                    assertThat(line.price()).isEqualByComparingTo("3.00");
                    assertThat(line.amount()).isEqualByComparingTo("9.00");
                });
    }

    @Test
    void noPriceCoverageReturnsClearError() {
        UserEntity user = saveUser("Alice", "REF-1", 1);
        saveReading(user, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(user, Product.GAS, "2024-01-31T23:59:59+02:00", "2.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-10", "1.00", 1);

        assertThatThrownBy(() -> billingService.generateInvoices(2024, 1))
                .isInstanceOf(IncompletePriceCoverageException.class)
                .hasMessageContaining("Price periods do not cover");
    }

    @Test
    void multiplePricesDoNotThrowAndCreateMultipleLines() {
        UserEntity user = saveUser("Alice", "REF-1", 1);
        saveReading(user, Product.GAS, "2024-01-01T00:00:00+02:00", "0.00");
        saveReading(user, Product.GAS, "2024-01-31T23:59:59+02:00", "31.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-15", "1.00", 1);
        savePrice(Product.GAS, "2024-01-16", "2024-01-31", "2.00", 1);

        Invoice invoice = billingService.generateInvoices(2024, 1).invoices().getFirst();

        assertThat(invoice.lines()).hasSize(2);
    }

    @Test
    void duplicateGenerationDoesNotCreateNewInvoice() {
        UserEntity user = saveUser("Alice", "REF-1", 1);
        saveReading(user, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(user, Product.GAS, "2024-01-31T23:59:59+02:00", "3.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "1.00", 1);

        InvoiceGenerationResult first = billingService.generateInvoices(2024, 1);
        InvoiceGenerationResult second = billingService.generateInvoices(2024, 1);

        assertThat(first.generatedCount()).isEqualTo(1);
        assertThat(second.generatedCount()).isZero();
        assertThat(invoiceRepository.count()).isEqualTo(1);
        assertThat(second.invoices().getFirst().documentNumber()).isEqualTo(first.invoices().getFirst().documentNumber());
    }

    @Test
    void invoiceNumberStartsAt1000AndContinuesFromDatabase() {
        UserEntity firstUser = saveUser("Alice", "REF-1", 1);
        saveReading(firstUser, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(firstUser, Product.GAS, "2024-01-31T23:59:59+02:00", "3.00");
        savePrice(Product.GAS, "2024-01-01", "2024-12-31", "1.00", 1);

        InvoiceGenerationResult january = billingService.generateInvoices(2024, 1);

        UserEntity secondUser = saveUser("Bob", "REF-2", 1);
        saveReading(secondUser, Product.GAS, "2024-02-01T00:00:00+02:00", "1.00");
        saveReading(secondUser, Product.GAS, "2024-02-29T23:59:59+02:00", "4.00");

        InvoiceGenerationResult february = billingService.generateInvoices(2024, 2);

        assertThat(january.invoices()).extracting(Invoice::documentNumber).containsExactly("1000");
        assertThat(february.invoices()).extracting(Invoice::documentNumber).containsExactly("1001");
    }

    @Test
    void noImportedDataReturnsClearError() {
        assertThatThrownBy(() -> billingService.generateInvoices(2024, 1))
                .isInstanceOf(NoImportedDataException.class)
                .hasMessage("No imported data found. Please import CSV files first.");
    }

    private UserEntity saveUser(String name, String reference, int priceList) {
        return userRepository.save(new UserEntity(name, reference, priceList));
    }

    private void saveReading(UserEntity user, Product product, String dateTime, String lastReading) {
        readingRepository.save(new ReadingEntity(user, product, OffsetDateTime.parse(dateTime), new BigDecimal(lastReading)));
    }

    private void savePrice(Product product, String startDate, String endDate, String price, int priceList) {
        priceRepository.save(new PriceEntity(product, LocalDate.parse(startDate), LocalDate.parse(endDate), new BigDecimal(price), priceList));
    }
}
