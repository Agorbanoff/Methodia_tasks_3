package com.methodia.minibilling.service;

import com.methodia.minibilling.service.billing.BillingRunService;
import com.methodia.minibilling.service.billing.BillingService;
import com.methodia.minibilling.service.billing.TariffSnapshotService;
import com.methodia.minibilling.service.audit.BillingErrorLogService;

import com.methodia.minibilling.controller.dto.billing.BillingRunRequest;
import com.methodia.minibilling.model.billing.BillingRunStatus;
import com.methodia.minibilling.model.billing.BillingRunItemStatus;
import com.methodia.minibilling.model.error.ErrorSeverity;
import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.model.reading.ReadingSource;
import com.methodia.minibilling.persistence.entity.BillingRunEntity;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.BillingRunItemRepository;
import com.methodia.minibilling.repository.BillingRunRepository;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.FileImportRepository;
import com.methodia.minibilling.repository.InvoiceLineRepository;
import com.methodia.minibilling.repository.InvoiceRepository;
import com.methodia.minibilling.repository.PriceRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.repository.SelfReportRepository;
import com.methodia.minibilling.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BillingRunLifecycleTest {

    @Autowired
    private BillingRunService billingRunService;

    @Autowired
    private BillingService billingService;

    @Autowired
    private TariffSnapshotService tariffSnapshotService;

    @Autowired
    private BillingRunRepository billingRunRepository;

    @Autowired
    private BillingRunItemRepository billingRunItemRepository;

    @Autowired
    private BillingErrorLogService billingErrorLogService;

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
        cleanDatabase();
        saveAdmin();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void startCreatesItemsAndCompletesAllCustomers() throws Exception {
        CustomerEntity firstCustomer = saveCustomer("Alice", "REF-1", 1);
        CustomerEntity secondCustomer = saveCustomer("Bob", "REF-2", 1);
        saveMonthlyReadings(firstCustomer, "1.00", "3.00");
        saveMonthlyReadings(secondCustomer, "2.00", "5.00");
        savePrice(Product.GAS, "2026-07-01", "2026-07-31", "1.00", 1);

        var response = billingRunService.start(
                new BillingRunRequest("2026-07-01", "2026-07-31", "all"),
                "admin"
        );

        assertThat(response.status()).isEqualTo(BillingRunStatus.IN_PROGRESS);
        assertThat(response.totalRecords()).isEqualTo(2);

        BillingRunEntity completedRun = waitForStatus(response.id(), BillingRunStatus.COMPLETED);

        assertThat(completedRun.getProcessedRecords()).isEqualTo(2);
        assertThat(completedRun.getFailedRecords()).isZero();
        assertThat(billingRunItemRepository.findByBillingRunOrderByCustomerReferenceAsc(completedRun)).hasSize(2);
        assertThat(invoiceRepository.count()).isEqualTo(2);
    }

    @Test
    void customerFailureDoesNotStopRemainingCustomers() throws Exception {
        CustomerEntity brokenCustomer = saveCustomer("Broken", "REF-1", 1);
        CustomerEntity validCustomer = saveCustomer("Valid", "REF-2", 1);
        saveReading(brokenCustomer, Product.GAS, "2026-07-01T00:00:00+03:00", "1.00");
        saveMonthlyReadings(validCustomer, "1.00", "4.00");
        savePrice(Product.GAS, "2026-07-01", "2026-07-31", "1.00", 1);

        var response = billingRunService.start(
                new BillingRunRequest("2026-07-01", "2026-07-31", "all"),
                "admin"
        );

        BillingRunEntity completedRun = waitForStatus(response.id(), BillingRunStatus.COMPLETED);

        assertThat(completedRun.getProcessedRecords()).isEqualTo(1);
        assertThat(completedRun.getFailedRecords()).isEqualTo(1);
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    @Test
    void moreThanFiftyPercentIncreaseCreatesWarningAndInvoice() throws Exception {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReadingsForWarning(customer, "0.00", "100.00", "100.00", "251.00");
        savePrice(Product.GAS, "2026-06-01", "2026-07-31", "1.00", 1);

        var response = billingRunService.start(
                new BillingRunRequest("2026-07-01", "2026-07-31", "REF-1"),
                "admin"
        );

        BillingRunEntity completedRun = waitForStatus(response.id(), BillingRunStatus.COMPLETED);

        assertThat(completedRun.getWarningRecords()).isEqualTo(1);
        assertThat(completedRun.getFailedRecords()).isZero();
        assertThat(invoiceRepository.count()).isEqualTo(1);
        assertThat(billingRunItemRepository.findByBillingRunOrderByCustomerReferenceAsc(completedRun))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getStatus()).isEqualTo(BillingRunItemStatus.WARNING);
                    assertThat(item.getSeverity()).isEqualTo(ErrorSeverity.WARNING);
                });
        assertThat(billingErrorLogService.list(org.springframework.data.domain.PageRequest.of(0, 20)).getContent())
                .singleElement()
                .satisfies(log -> assertThat(log.severity()).isEqualTo(ErrorSeverity.WARNING));
    }

    @Test
    void moreThanFiftyPercentDecreaseCreatesWarningAndInvoice() throws Exception {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReadingsForWarning(customer, "0.00", "100.00", "100.00", "149.00");
        savePrice(Product.GAS, "2026-06-01", "2026-07-31", "1.00", 1);

        var response = billingRunService.start(
                new BillingRunRequest("2026-07-01", "2026-07-31", "REF-1"),
                "admin"
        );

        BillingRunEntity completedRun = waitForStatus(response.id(), BillingRunStatus.COMPLETED);

        assertThat(completedRun.getWarningRecords()).isEqualTo(1);
        assertThat(completedRun.getFailedRecords()).isZero();
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    @Test
    void exactlyFiftyPercentDeviationDoesNotCreateWarning() throws Exception {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReadingsForWarning(customer, "0.00", "100.00", "100.00", "250.00");
        savePrice(Product.GAS, "2026-06-01", "2026-07-31", "1.00", 1);

        var response = billingRunService.start(
                new BillingRunRequest("2026-07-01", "2026-07-31", "REF-1"),
                "admin"
        );

        BillingRunEntity completedRun = waitForStatus(response.id(), BillingRunStatus.COMPLETED);

        assertThat(completedRun.getWarningRecords()).isZero();
        assertThat(billingRunItemRepository.findByBillingRunOrderByCustomerReferenceAsc(completedRun))
                .singleElement()
                .satisfies(item -> assertThat(item.getStatus()).isEqualTo(BillingRunItemStatus.PROCESSED));
    }

    @Test
    void previousZeroConsumptionDoesNotCreateWarning() throws Exception {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveReadingsForWarning(customer, "0.00", "0.00", "0.00", "10.00");
        savePrice(Product.GAS, "2026-06-01", "2026-07-31", "1.00", 1);

        var response = billingRunService.start(
                new BillingRunRequest("2026-07-01", "2026-07-31", "REF-1"),
                "admin"
        );

        BillingRunEntity completedRun = waitForStatus(response.id(), BillingRunStatus.COMPLETED);

        assertThat(completedRun.getWarningRecords()).isZero();
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    @Test
    void firstBillingPeriodDoesNotCreateWarning() throws Exception {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveMonthlyReadings(customer, "100.00", "251.00");
        savePrice(Product.GAS, "2026-07-01", "2026-07-31", "1.00", 1);

        var response = billingRunService.start(
                new BillingRunRequest("2026-07-01", "2026-07-31", "REF-1"),
                "admin"
        );

        BillingRunEntity completedRun = waitForStatus(response.id(), BillingRunStatus.COMPLETED);

        assertThat(completedRun.getWarningRecords()).isZero();
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    @Test
    void billingReportUsesOnlyRequestedRunItems() throws Exception {
        CustomerEntity warningCustomer = saveCustomer("Alice", "REF-1", 1);
        CustomerEntity normalCustomer = saveCustomer("Bob", "REF-2", 1);
        saveReadingsForWarning(warningCustomer, "0.00", "100.00", "100.00", "251.00");
        saveMonthlyReadings(normalCustomer, "1.00", "4.00");
        savePrice(Product.GAS, "2026-06-01", "2026-07-31", "1.00", 1);

        var response = billingRunService.start(
                new BillingRunRequest("2026-07-01", "2026-07-31", "all"),
                "admin"
        );
        waitForStatus(response.id(), BillingRunStatus.COMPLETED);

        var report = billingRunService.report(response.id(), "admin");

        assertThat(report.status()).isEqualTo(BillingRunStatus.COMPLETED);
        assertThat(report.processedRecords()).isEqualTo(2);
        assertThat(report.successfulInvoices()).isEqualTo(1);
        assertThat(report.warningRecords()).isEqualTo(1);
        assertThat(report.failedRecords()).isZero();
        assertThat(report.duration()).isNotNegative();
    }

    @Test
    void restartCreatesNewRunAndDoesNotDuplicateExistingInvoices() throws Exception {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveMonthlyReadings(customer, "1.00", "3.00");
        savePrice(Product.GAS, "2026-07-01", "2026-07-31", "1.00", 1);

        var firstRun = billingRunService.start(
                new BillingRunRequest("2026-07-01", "2026-07-31", "REF-1"),
                "admin"
        );
        waitForStatus(firstRun.id(), BillingRunStatus.COMPLETED);

        var restartedRun = billingRunService.restart(firstRun.id(), "admin");
        waitForStatus(restartedRun.id(), BillingRunStatus.COMPLETED);

        assertThat(restartedRun.id()).isNotEqualTo(firstRun.id());
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    @Test
    void frozenTariffSnapshotKeepsOriginalPrice() {
        CustomerEntity customer = saveCustomer("Alice", "REF-1", 1);
        saveMonthlyReadings(customer, "1.00", "3.00");
        PriceEntity originalPrice = savePrice(Product.GAS, "2026-07-01", "2026-07-31", "1.00", 1);
        String snapshot = tariffSnapshotService.createSnapshot(customer);

        priceRepository.delete(originalPrice);
        savePrice(Product.GAS, "2026-07-01", "2026-07-31", "9.00", 1);

        billingService.generateInvoiceForCustomer(
                customer,
                YearMonth.of(2026, 7),
                tariffSnapshotService.readSnapshot(snapshot)
        );

        assertThat(invoiceLineRepository.findAll()).singleElement()
                .satisfies(line -> assertThat(line.getPrice()).isEqualByComparingTo("1.00"));
    }

    private BillingRunEntity waitForStatus(String runId, BillingRunStatus status) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            BillingRunEntity run = billingRunRepository.findById(runId).orElseThrow();
            if (run.getStatus() == status) {
                return run;
            }
            Thread.sleep(100);
        }
        return billingRunRepository.findById(runId).orElseThrow();
    }

    private void cleanDatabase() {
        try {
            Files.deleteIfExists(Path.of("logs/error.log"));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to clean error log", exception);
        }
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

    private void saveAdmin() {
        UserEntity admin = new UserEntity(null, "Administrator", "admin", 0, new ArrayList<>());
        admin.setUsername("admin");
        admin.setPasswordHash("hash");
        admin.setRole("ADMIN");
        userRepository.save(admin);
    }

    private CustomerEntity saveCustomer(String name, String reference, int priceList) {
        return customerRepository.save(new CustomerEntity(reference, name, "T" + priceList));
    }

    private void saveMonthlyReadings(CustomerEntity customer, String firstReading, String lastReading) {
        saveReading(customer, Product.GAS, "2026-07-01T00:00:00+03:00", firstReading);
        saveReading(customer, Product.GAS, "2026-07-31T23:59:59+03:00", lastReading);
    }

    private void saveReadingsForWarning(CustomerEntity customer, String juneStart, String juneEnd,
                                        String julyStart, String julyEnd) {
        saveReading(customer, Product.GAS, "2026-06-01T00:00:00+03:00", juneStart);
        saveReading(customer, Product.GAS, "2026-06-30T23:59:59+03:00", juneEnd);
        saveReading(customer, Product.GAS, "2026-07-01T00:00:00+03:00", julyStart);
        saveReading(customer, Product.GAS, "2026-07-31T23:59:59+03:00", julyEnd);
    }

    private void saveReading(CustomerEntity customer, Product product, String dateTime, String lastReading) {
        readingRepository.save(new ReadingEntity(null, customer, product, OffsetDateTime.parse(dateTime),
                new BigDecimal(lastReading), false, false, ReadingSource.IMPORTED, null));
    }

    private PriceEntity savePrice(Product product, String startDate, String endDate, String price, int priceList) {
        return priceRepository.save(new PriceEntity(null, product, LocalDate.parse(startDate), LocalDate.parse(endDate),
                new BigDecimal(price), priceList, null));
    }
}
