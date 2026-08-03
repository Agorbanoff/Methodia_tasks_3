package com.methodia.minibilling.service;

import com.methodia.minibilling.service.billing.BillingService;
import com.methodia.minibilling.service.billing.InvoiceGenerationResult;
import com.methodia.minibilling.service.invoice.InvoiceQueryService;

import com.methodia.minibilling.exception.IncompletePriceCoverageException;
import com.methodia.minibilling.exception.NoImportedDataException;
import com.methodia.minibilling.model.invoice.Invoice;
import com.methodia.minibilling.model.invoice.InvoiceLine;
import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.model.reading.ReadingSource;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.BillingRunItemRepository;
import com.methodia.minibilling.repository.BillingRunRepository;
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

    @Autowired
    private BillingRunItemRepository billingRunItemRepository;

    @Autowired
    private BillingRunRepository billingRunRepository;

    @BeforeEach
    void setUp() {
        billingRunItemRepository.deleteAllInBatch();
        billingRunRepository.deleteAllInBatch();
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

        assertThat(consumptionLines(invoice)).hasSize(1);
        assertThat(consumptionLines(invoice).getFirst().quantity()).isEqualByComparingTo("10.00");
        assertThat(consumptionLines(invoice).getFirst().price()).isEqualByComparingTo("2.00");
        assertThat(consumptionLines(invoice).getFirst().amount()).isEqualByComparingTo("20.00");
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

        assertThat(consumptionLines(invoice)).hasSize(3);
        assertThat(consumptionLines(invoice)).extracting(InvoiceLine::index).containsExactly(1, 2, 3);
        assertThat(consumptionLines(invoice)).extracting(InvoiceLine::amount)
                .containsExactly(new BigDecimal("10.23"), new BigDecimal("20.46"), new BigDecimal("31.62"));
        assertThat(consumptionLines(invoice).stream().map(InvoiceLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("62.31");
    }

    @Test
    void usesPricesTwoWhenUserHasPriceListTwo() {
        CustomerEntity customer = saveCustomer("Bob", "REF-2", 2);
        saveReading(customer, Product.GAS, "2024-01-01T00:00:00+02:00", "5.00");
        saveReading(customer, Product.GAS, "2024-01-31T23:59:59+02:00", "8.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "100.00", 1);
        savePrice(Product.GAS, "2024-01-01", "2024-01-31", "2.00", 2);

        Invoice invoice = billingService.generateInvoices(2024, 1).invoices().getFirst();

        assertThat(consumptionLines(invoice)).singleElement()
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

        assertThat(consumptionLines(invoice)).singleElement()
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
    void finalTaskConcreteInputCreatesConsumptionStandingChargeCclAndVatSnapshot() {
        int priceList = 1;
        CustomerEntity customer = customerRepository.save(new CustomerEntity("1002", "Maria Petrova Petrova", priceList));
        saveReading(customer, Product.GAS, "2025-01-01T00:00:00+02:00", "0.00");
        saveReading(customer, Product.GAS, "2025-03-11T23:59:59+02:00", "436.00");
        saveOnlyPrice(Product.GAS, "2025-01-01", "2025-01-31", "1.80", priceList);
        saveOnlyPrice(Product.GAS, "2025-02-01", "2025-12-31", "2.00", priceList);
        saveOnlyPrice(Product.STANDING_CHARGE, "2025-01-01", "2025-01-31", "1.60", priceList);
        saveOnlyPrice(Product.STANDING_CHARGE, "2025-02-01", "2025-12-31", "1.80", priceList);
        saveOnlyPrice(Product.CCL, "2025-01-01", "2025-01-31", "0.02", priceList);
        saveOnlyPrice(Product.CCL, "2025-02-01", "2025-12-31", "0.03", priceList);

        Invoice invoice = billingService.generateInvoices(2025, 3).invoices().getFirst();

        assertThat(invoice.lines()).hasSize(6);
        assertThat(invoice.lines()).extracting(InvoiceLine::index).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(consumptionLines(invoice)).hasSize(2);
        assertThat(consumptionLines(invoice).stream().map(InvoiceLine::quantity).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("436.00");
        var standingChargeLines = invoice.lines().stream()
                .filter(line -> "Standing charge".equals(line.name()))
                .toList();
        assertThat(standingChargeLines).extracting(InvoiceLine::quantity)
                .containsExactly(new BigDecimal("31.00"), new BigDecimal("39.00"));
        assertThat(standingChargeLines).extracting(InvoiceLine::amount)
                .containsExactly(new BigDecimal("49.60"), new BigDecimal("70.20"));
        assertThat(standingChargeLines.stream().map(InvoiceLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("119.80");
        var cclLines = invoice.lines().stream().filter(line -> "CCL".equals(line.name())).toList();
        assertThat(cclLines.stream().map(InvoiceLine::quantity).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("436.00");
        assertThat(standingChargeLines).extracting(InvoiceLine::lines)
                .containsExactly(java.util.List.of(1), java.util.List.of(2));
        assertThat(cclLines).extracting(InvoiceLine::lines)
                .containsExactly(java.util.List.of(1), java.util.List.of(2));
        assertThat(invoice.totalAmountWithVat()).isEqualByComparingTo(
                invoice.totalAmount().multiply(new BigDecimal("1.20")).setScale(2, java.math.RoundingMode.UP));
        assertThat(invoiceRepository.findByNumber(invoice.documentNumber()).orElseThrow().getTotalAmountWithVat())
                .isEqualByComparingTo(invoice.totalAmountWithVat());
    }

    @Test
    void multiplePricesDoNotThrowAndCreateMultipleLines() {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReading(customer, Product.GAS, "2024-01-01T00:00:00+02:00", "0.00");
        saveReading(customer, Product.GAS, "2024-01-31T23:59:59+02:00", "31.00");
        savePrice(Product.GAS, "2024-01-01", "2024-01-15", "1.00", 1);
        savePrice(Product.GAS, "2024-01-16", "2024-01-31", "2.00", 1);

        Invoice invoice = billingService.generateInvoices(2024, 1).invoices().getFirst();

        assertThat(consumptionLines(invoice)).hasSize(2);
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
                "1000", customer, BigDecimal.ONE, new BigDecimal("1.20"), false, 2024, 1, new ArrayList<>()));

        assertThatThrownBy(() -> invoiceRepository.saveAndFlush(new InvoiceEntity(null,
                OffsetDateTime.parse("2024-01-31T12:01:00+02:00"),
                "1001", customer, BigDecimal.TEN, new BigDecimal("12.00"), false, 2024, 1, new ArrayList<>())))
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
        return customerRepository.save(new CustomerEntity(reference, name, priceList));
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
        if (product == Product.GAS || product == Product.ELECT) {
            ensureChargePrice(Product.STANDING_CHARGE, startDate, endDate, "1.00", priceList);
            ensureChargePrice(Product.CCL, startDate, endDate, "0.01", priceList);
        }
    }

    private void ensureChargePrice(Product product, String startDate, String endDate, String price, int priceList) {
        if (!priceRepository.existsByPriceListAndProductAndStartDateAndEndDate(
                priceList, product, LocalDate.parse(startDate), LocalDate.parse(endDate))) {
            priceRepository.save(new PriceEntity(null, product, LocalDate.parse(startDate), LocalDate.parse(endDate),
                    new BigDecimal(price), priceList, null));
        }
    }

    private void saveOnlyPrice(Product product, String startDate, String endDate, String price, int priceList) {
        PriceEntity entity = new PriceEntity(null, product, LocalDate.parse(startDate), LocalDate.parse(endDate),
                new BigDecimal(price), priceList, null);
        priceRepository.save(entity);
    }

    private java.util.List<InvoiceLine> consumptionLines(Invoice invoice) {
        return invoice.lines().stream()
                .filter(line -> line.name() == null)
                .toList();
    }
}
