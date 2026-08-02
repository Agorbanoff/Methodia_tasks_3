package com.methodia.minibilling.service;

import com.methodia.minibilling.exception.IncompletePriceCoverageException;
import com.methodia.minibilling.exception.NoImportedDataException;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.InvoiceLine;
import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.model.ReadingSource;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.FileImportRepository;
import com.methodia.minibilling.repository.InvoiceLineRepository;
import com.methodia.minibilling.repository.InvoiceRepository;
import com.methodia.minibilling.repository.PriceRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.repository.SelfReportRepository;
import com.methodia.minibilling.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class BillingDatabaseServiceTest {

    @Autowired
    private BillingService billingService;

    @Autowired
    private InvoiceQueryService invoiceQueryService;

    @Autowired
    private CustomerRepository customerRepository;

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

    @Autowired
    private SelfReportRepository selfReportRepository;

    @BeforeEach
    void setUp() {
        invoiceLineRepository.deleteAllInBatch();
        invoiceRepository.deleteAllInBatch();
        selfReportRepository.deleteAllInBatch();
        readingRepository.deleteAllInBatch();
        priceRepository.deleteAllInBatch();
        fileImportRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    @Test
    void consumerWithSinglePriceCreatesOneInvoiceLine() {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReading(customer, Product.GAS, "2024-01-01T00:00:00+02:00", "10.00");
        saveReading(customer, Product.GAS, "2024-01-31T23:59:59+02:00", "20.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "2.00", 1);

        Invoice invoice = billingService.generateInvoices(2024, 1).invoices().getFirst();

        assertThat(invoice.lines()).hasSize(1);
        assertThat(invoice.lines().getFirst().quantity()).isEqualByComparingTo("10.00");
        assertThat(invoice.lines().getFirst().price()).isEqualByComparingTo("2.00");
        assertThat(invoice.lines().getFirst().amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void consumerWithThreePricesCreatesThreeInvoiceLinesAndTotalAmountIsSum() {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReading(customer, Product.ELECT, "2024-01-01T00:00:00+02:00", "0.00");
        saveReading(customer, Product.ELECT, "2024-01-31T23:59:59+02:00", "31.00");
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
        CustomerEntity customer = saveCustomer("Bob", "REF-2", 2);
        saveReading(customer, Product.GAS, "2024-01-01T00:00:00+02:00", "5.00");
        saveReading(customer, Product.GAS, "2024-01-31T23:59:59+02:00", "8.00");
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
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReading(customer, Product.GAS, "2024-01-01T00:00:00+02:00", "5.00");
        saveReading(customer, Product.GAS, "2024-01-31T23:59:59+02:00", "8.00");
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
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReading(customer, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(customer, Product.GAS, "2024-01-31T23:59:59+02:00", "2.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-10", "1.00", 1);

        assertThatThrownBy(() -> billingService.generateInvoices(2024, 1))
                .isInstanceOf(IncompletePriceCoverageException.class)
                .hasMessageContaining("Price periods do not cover");
    }

    @Test
    void multiplePricesDoNotThrowAndCreateMultipleLines() {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReading(customer, Product.GAS, "2024-01-01T00:00:00+02:00", "0.00");
        saveReading(customer, Product.GAS, "2024-01-31T23:59:59+02:00", "31.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-15", "1.00", 1);
        savePrice(Product.GAS, "2024-01-16", "2024-01-31", "2.00", 1);

        Invoice invoice = billingService.generateInvoices(2024, 1).invoices().getFirst();

        assertThat(invoice.lines()).hasSize(2);
    }

    @Test
    void duplicateGenerationDoesNotCreateNewInvoice() {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReading(customer, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(customer, Product.GAS, "2024-01-31T23:59:59+02:00", "3.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "1.00", 1);

        InvoiceGenerationResult first = billingService.generateInvoices(2024, 1);
        InvoiceGenerationResult second = billingService.generateInvoices(2024, 1);

        assertThat(first.generatedCount()).isEqualTo(1);
        assertThat(second.generatedCount()).isZero();
        assertThat(invoiceRepository.count()).isEqualTo(1);
        assertThat(second.invoices().getFirst().documentNumber()).isEqualTo(first.invoices().getFirst().documentNumber());
    }

    @Test
    void databaseRejectsDuplicateInvoiceForSameCustomerAndPeriod() {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        invoiceRepository.saveAndFlush(new InvoiceEntity(null, OffsetDateTime.parse("2024-01-31T12:00:00+02:00"),
                "1000", customer, BigDecimal.ONE, false, 2024, 1, new ArrayList<>()));

        assertThatThrownBy(() -> invoiceRepository.saveAndFlush(new InvoiceEntity(null,
                OffsetDateTime.parse("2024-01-31T12:01:00+02:00"),
                "1001", customer, BigDecimal.TEN, false, 2024, 1, new ArrayList<>())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invoiceNumberStartsAt1000AndContinuesFromDatabase() {
        CustomerEntity firstCustomer = saveCustomer("Alice", "REF-1", 1);
        saveReading(firstCustomer, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(firstCustomer, Product.GAS, "2024-01-31T23:59:59+02:00", "3.00");
        savePrice(Product.GAS, "2024-01-01", "2024-12-31", "1.00", 1);

        InvoiceGenerationResult january = billingService.generateInvoices(2024, 1);

        CustomerEntity secondCustomer = saveCustomer("Bob", "REF-2", 1);
        saveReading(secondCustomer, Product.GAS, "2024-02-01T00:00:00+02:00", "1.00");
        saveReading(secondCustomer, Product.GAS, "2024-02-29T23:59:59+02:00", "4.00");

        InvoiceGenerationResult february = billingService.generateInvoices(2024, 2);

        assertThat(january.invoices()).extracting(Invoice::documentNumber).containsExactly("1000");
        assertThat(february.invoices()).extracting(Invoice::documentNumber).containsExactly("1001");
    }

    @Test
    void adminAccountDoesNotReceiveInvoice() {
        UserEntity admin = new UserEntity(null, "Administrator", "admin", 0, new java.util.ArrayList<>());
        admin.setUsername("admin");
        admin.setRole("ADMIN");
        userRepository.save(admin);

        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReading(customer, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(customer, Product.GAS, "2024-01-31T23:59:59+02:00", "3.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "1.00", 1);

        InvoiceGenerationResult result = billingService.generateInvoices(2024, 1);

        assertThat(result.generatedCount()).isEqualTo(1);
        assertThat(result.invoices()).extracting(Invoice::reference).containsExactly("REF-1");
    }

    @Test
    void userInvoiceLookupIgnoresRequestedOtherCustomer() {
        CustomerEntity firstCustomer = saveCustomer("Alice", "REF-1", 1);
        CustomerEntity secondCustomer = saveCustomer("Bob", "REF-2", 1);
        saveCustomerAccount("alice", firstCustomer);
        saveCustomerAccount("bob", secondCustomer);
        saveReading(firstCustomer, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(firstCustomer, Product.GAS, "2024-01-31T23:59:59+02:00", "3.00");
        saveReading(secondCustomer, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(secondCustomer, Product.GAS, "2024-01-31T23:59:59+02:00", "4.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "1.00", 1);
        billingService.generateInvoices(2024, 1);

        var visible = invoiceQueryService.findVisibleInvoices(
                java.util.Optional.of("REF-2"), Pageable.unpaged(), "alice");

        assertThat(visible.getContent()).extracting(invoice -> invoice.reference()).containsExactly("REF-1");
    }

    @Test
    void adminInvoiceLookupSeesAllCustomers() {
        CustomerEntity firstCustomer = saveCustomer("Alice", "REF-1", 1);
        CustomerEntity secondCustomer = saveCustomer("Bob", "REF-2", 1);
        saveAdminAccount("admin");
        saveReading(firstCustomer, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(firstCustomer, Product.GAS, "2024-01-31T23:59:59+02:00", "3.00");
        saveReading(secondCustomer, Product.GAS, "2024-01-01T00:00:00+02:00", "1.00");
        saveReading(secondCustomer, Product.GAS, "2024-01-31T23:59:59+02:00", "4.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "1.00", 1);
        billingService.generateInvoices(2024, 1);

        var visible = invoiceQueryService.findVisibleInvoices(
                java.util.Optional.empty(), Pageable.unpaged(), "admin");

        assertThat(visible.getContent()).extracting(invoice -> invoice.reference()).containsExactly("REF-1", "REF-2");
    }

    @Test
    void noImportedDataReturnsClearError() {
        assertThatThrownBy(() -> billingService.generateInvoices(2024, 1))
                .isInstanceOf(NoImportedDataException.class)
                .hasMessage("No imported data found. Please import CSV files first.");
    }

    private CustomerEntity saveCustomer(String name, String reference, int priceList) {
        return customerRepository.save(new CustomerEntity(reference, name, "T" + priceList));
    }

    private void saveReading(CustomerEntity customer, Product product, String dateTime, String lastReading) {
        readingRepository.save(new ReadingEntity(null, customer, product, OffsetDateTime.parse(dateTime),
                new BigDecimal(lastReading), false, false, ReadingSource.IMPORTED, null));
    }

    private void saveCustomerAccount(String username, CustomerEntity customer) {
        UserEntity account = new UserEntity(null, customer.getName(), customer.getReference(), 1, new java.util.ArrayList<>());
        account.setUsername(username);
        account.setPasswordHash("hash");
        account.setRole("USER");
        account.setCustomer(customer);
        userRepository.save(account);
    }

    private void saveAdminAccount(String username) {
        UserEntity account = new UserEntity(null, "Administrator", username, 0, new java.util.ArrayList<>());
        account.setUsername(username);
        account.setPasswordHash("hash");
        account.setRole("ADMIN");
        userRepository.save(account);
    }

    private void savePrice(Product product, String startDate, String endDate, String price, int priceList) {
        priceRepository.save(new PriceEntity(null, product, LocalDate.parse(startDate), LocalDate.parse(endDate), new BigDecimal(price), priceList, null));
    }
}
