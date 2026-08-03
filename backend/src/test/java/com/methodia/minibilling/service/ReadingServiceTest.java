package com.methodia.minibilling.service;

import com.methodia.minibilling.service.reading.ReadingService;

import com.methodia.minibilling.controller.dto.reading.SelfReportRequest;
import com.methodia.minibilling.exception.SelfReportStateException;
import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.model.reading.ReadingSource;
import com.methodia.minibilling.model.reading.SelfReportStatus;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.repository.SelfReportRepository;
import com.methodia.minibilling.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "app.audit.log-file=target/test-audit-readings.log")
class ReadingServiceTest {

    @Autowired
    private ReadingService readingService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private SelfReportRepository selfReportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                truncate table invoice_lines, invoices, readings, prices, file_imports,
                self_reports, billing_run_items, billing_runs, users, customers restart identity cascade
                """);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void userCreatesPendingSelfReportForLinkedCustomer() {
        CustomerEntity customer = saveCustomer("DUMMY-1001", "Acme");
        saveUser("alice", "USER", customer);

        var response = readingService.submitSelfReport(
                new SelfReportRequest(LocalDate.parse("2026-08-02"), "elec", new BigDecimal("452.7")),
                "alice"
        );

        assertThat(response.reference()).isEqualTo("DUMMY-1001");
        assertThat(response.consumer()).isEqualTo("Acme");
        assertThat(response.service()).isEqualTo("elec");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(readingRepository.count()).isZero();
    }

    @Test
    void duplicatePendingSelfReportReturnsConflict() {
        CustomerEntity customer = saveCustomer("DUMMY-1001", "Acme");
        saveUser("alice", "USER", customer);
        SelfReportRequest request = new SelfReportRequest(LocalDate.parse("2026-08-02"), "gas", new BigDecimal("10"));
        readingService.submitSelfReport(request, "alice");

        assertThatThrownBy(() -> readingService.submitSelfReport(request, "alice"))
                .isInstanceOf(SelfReportStateException.class)
                .hasMessageContaining("pending self report already exists");
    }

    @Test
    void selfReportCannotBeLowerThanPreviousReading() {
        CustomerEntity customer = saveCustomer("DUMMY-1001", "Acme");
        saveUser("alice", "USER", customer);
        saveReading(customer, Product.GAS, "2026-08-01T00:00:00+03:00", "500.00", ReadingSource.IMPORTED);

        assertThatThrownBy(() -> readingService.submitSelfReport(
                new SelfReportRequest(LocalDate.parse("2026-08-02"), "gas", new BigDecimal("450")),
                "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be lower than the previous cumulative reading");
    }

    @Test
    void invalidServiceNegativeAmountAndFutureDateAreRejected() {
        CustomerEntity customer = saveCustomer("DUMMY-1001", "Acme");
        saveUser("alice", "USER", customer);

        assertThatThrownBy(() -> readingService.submitSelfReport(
                new SelfReportRequest(LocalDate.parse("2026-08-02"), "water", new BigDecimal("1")),
                "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service must be gas or elec");

        assertThatThrownBy(() -> readingService.submitSelfReport(
                new SelfReportRequest(LocalDate.parse("2026-08-02"), "gas", new BigDecimal("-1")),
                "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");

        assertThatThrownBy(() -> readingService.submitSelfReport(
                new SelfReportRequest(LocalDate.now().plusDays(1), "gas", new BigDecimal("1")),
                "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be in the future");
    }

    @Test
    void adminAndUserSelfReportListingsRespectOwnershipAndFilters() {
        CustomerEntity firstCustomer = saveCustomer("DUMMY-1001", "Acme");
        CustomerEntity secondCustomer = saveCustomer("DUMMY-2002", "Beta");
        saveUser("alice", "USER", firstCustomer);
        saveUser("bob", "USER", secondCustomer);
        saveUser("admin", "ADMIN", null);
        readingService.submitSelfReport(new SelfReportRequest(LocalDate.parse("2026-08-02"), "gas", new BigDecimal("10")), "alice");
        readingService.submitSelfReport(new SelfReportRequest(LocalDate.parse("2026-08-02"), "elec", new BigDecimal("20")), "bob");

        var adminReports = readingService.listSelfReports(Optional.of("PENDING"), Optional.empty(),
                Optional.empty(), Pageable.unpaged(), "admin");
        var userReports = readingService.listSelfReports(Optional.empty(), Optional.of("DUMMY-2002"),
                Optional.empty(), Pageable.unpaged(), "alice");

        assertThat(adminReports.getContent()).hasSize(2);
        assertThat(userReports.getContent()).singleElement()
                .satisfies(report -> assertThat(report.reference()).isEqualTo("DUMMY-1001"));
    }

    @Test
    void adminAcceptsPendingReportAndCreatesSelfReportedReading() {
        CustomerEntity customer = saveCustomer("DUMMY-1001", "Acme");
        saveUser("alice", "USER", customer);
        saveUser("admin", "ADMIN", null);
        var report = readingService.submitSelfReport(
                new SelfReportRequest(LocalDate.parse("2026-08-02"), "elec", new BigDecimal("452.7")),
                "alice"
        );

        var accepted = readingService.acceptSelfReport(report.id(), "admin");

        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.reviewedAt()).isNotNull();
        assertThat(accepted.reviewedBy()).isEqualTo("admin");
        assertThat(readingRepository.findAll()).singleElement()
                .satisfies(reading -> {
                    assertThat(reading.getSource()).isEqualTo(ReadingSource.SELF_REPORTED);
                    assertThat(reading.getLastReading()).isEqualByComparingTo("452.7");
                    assertThat(reading.getDateTime().atZoneSameInstant(java.time.ZoneId.of("Europe/Sofia")).toLocalDate())
                            .isEqualTo(LocalDate.parse("2026-08-02"));
                });
    }

    @Test
    void acceptingTwiceOrAfterDuplicateReadingReturnsConflict() {
        CustomerEntity customer = saveCustomer("DUMMY-1001", "Acme");
        saveUser("alice", "USER", customer);
        saveUser("admin", "ADMIN", null);
        var report = readingService.submitSelfReport(
                new SelfReportRequest(LocalDate.parse("2026-08-02"), "gas", new BigDecimal("100")),
                "alice"
        );
        readingService.acceptSelfReport(report.id(), "admin");

        assertThatThrownBy(() -> readingService.acceptSelfReport(report.id(), "admin"))
                .isInstanceOf(SelfReportStateException.class)
                .hasMessageContaining("already been processed");

        var secondReport = readingService.submitSelfReport(
                new SelfReportRequest(LocalDate.parse("2026-08-01"), "gas", new BigDecimal("110")),
                "alice"
        );
        saveReading(customer, Product.GAS, "2026-08-01T12:00:00+03:00", "110.00", ReadingSource.IMPORTED);

        assertThatThrownBy(() -> readingService.acceptSelfReport(secondReport.id(), "admin"))
                .isInstanceOf(SelfReportStateException.class)
                .hasMessageContaining("reading already exists");
        assertThat(selfReportRepository.findById(secondReport.id()).orElseThrow().getStatus())
                .isEqualTo(SelfReportStatus.PENDING);
    }

    @Test
    void adminDeniesPendingReportWithoutCreatingReading() {
        CustomerEntity customer = saveCustomer("DUMMY-1001", "Acme");
        saveUser("alice", "USER", customer);
        saveUser("admin", "ADMIN", null);
        var report = readingService.submitSelfReport(
                new SelfReportRequest(LocalDate.parse("2026-08-02"), "gas", new BigDecimal("100")),
                "alice"
        );

        var declined = readingService.denySelfReport(report.id(), "admin");

        assertThat(declined.status()).isEqualTo("DENIED");
        assertThat(declined.reviewedAt()).isNotNull();
        assertThat(declined.reviewedBy()).isEqualTo("admin");
        assertThat(readingRepository.count()).isZero();
        assertThatThrownBy(() -> readingService.denySelfReport(report.id(), "admin"))
                .isInstanceOf(SelfReportStateException.class)
                .hasMessageContaining("already been processed");
        assertThatThrownBy(() -> readingService.acceptSelfReport(report.id(), "admin"))
                .isInstanceOf(SelfReportStateException.class)
                .hasMessageContaining("already been processed");
    }

    @Test
    void readingListingsReturnImportedAndSelfReportedSourcesWithOwnership() {
        CustomerEntity firstCustomer = saveCustomer("DUMMY-1001", "Acme");
        CustomerEntity secondCustomer = saveCustomer("DUMMY-2002", "Beta");
        saveUser("alice", "USER", firstCustomer);
        saveUser("admin", "ADMIN", null);
        saveReading(firstCustomer, Product.GAS, "2026-08-01T00:00:00+03:00", "100", ReadingSource.IMPORTED);
        saveReading(secondCustomer, Product.ELECT, "2026-08-01T00:00:00+03:00", "200", ReadingSource.IMPORTED);
        var report = readingService.submitSelfReport(
                new SelfReportRequest(LocalDate.parse("2026-08-02"), "gas", new BigDecimal("110")),
                "alice"
        );
        readingService.acceptSelfReport(report.id(), "admin");

        var adminReadings = readingService.listReadings(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Pageable.unpaged(), "admin");
        var userReadings = readingService.listReadings(Optional.of("DUMMY-2002"), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Pageable.unpaged(), "alice");

        assertThat(adminReadings.getContent()).hasSize(3);
        assertThat(adminReadings.getContent()).extracting(reading -> reading.selfReported())
                .contains(false, true);
        assertThat(userReadings.getContent()).hasSize(2);
        assertThat(userReadings.getContent()).allSatisfy(reading ->
                assertThat(reading.reference()).isEqualTo("DUMMY-1001"));
    }

    private CustomerEntity saveCustomer(String reference, String name) {
        return customerRepository.save(new CustomerEntity(reference, name, "T1"));
    }

    private void saveUser(String username, String role, CustomerEntity customer) {
        UserEntity user = new UserEntity(null, username, username, 1, new ArrayList<>());
        user.setUsername(username);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setCustomer(customer);
        userRepository.save(user);
    }

    private void saveReading(CustomerEntity customer, Product product, String dateTime, String amount, ReadingSource source) {
        readingRepository.save(new ReadingEntity(null, customer, product, OffsetDateTime.parse(dateTime),
                new BigDecimal(amount), false, source == ReadingSource.SELF_REPORTED, source, null));
    }
}
