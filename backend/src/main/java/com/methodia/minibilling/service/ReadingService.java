package com.methodia.minibilling.service;

import com.methodia.minibilling.controller.dto.reading.ReadingResponse;
import com.methodia.minibilling.controller.dto.reading.SelfReportRequest;
import com.methodia.minibilling.controller.dto.reading.SelfReportResponse;
import com.methodia.minibilling.exception.SelfReportNotFoundException;
import com.methodia.minibilling.exception.SelfReportStateException;
import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.model.ReadingSource;
import com.methodia.minibilling.model.SelfReportStatus;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.persistence.entity.SelfReportEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.repository.SelfReportRepository;
import com.methodia.minibilling.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ReadingService {

    private static final ZoneId SOFIA_ZONE = ZoneId.of("Europe/Sofia");

    private final SelfReportRepository selfReportRepository;
    private final ReadingRepository readingRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final Clock clock;

    public ReadingService(SelfReportRepository selfReportRepository,
                          ReadingRepository readingRepository,
                          UserRepository userRepository,
                          CustomerRepository customerRepository,
                          AuditService auditService,
                          Clock clock) {
        this.selfReportRepository = selfReportRepository;
        this.readingRepository = readingRepository;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public SelfReportResponse submitSelfReport(SelfReportRequest request, String username) {
        UserEntity account = findAccount(username);
        CustomerEntity customer = linkedCustomer(account);
        Product service = parseService(request.service());
        validateSelfReportDate(request.date());
        validateAmount(request.amount());
        validateCumulativeReading(customer, service, request.date(), request.amount());

        boolean duplicatePending = selfReportRepository.existsByCustomerAndServiceAndReadingDateAndStatus(
                customer, service, request.date(), SelfReportStatus.PENDING);
        if (duplicatePending) {
            throw new SelfReportStateException("A pending self report already exists for this customer, service and date");
        }

        SelfReportEntity report = selfReportRepository.save(new SelfReportEntity(
                customer,
                service,
                request.date(),
                request.amount(),
                OffsetDateTime.now(clock)
        ));
        auditService.record("SELF_REPORT_CREATED", username, "READINGS",
                "reportId=%s reference=%s service=%s date=%s"
                        .formatted(report.getId(), customer.getReference(), serviceName(service), report.getReadingDate()));
        return toSelfReportResponse(report);
    }

    @Transactional(readOnly = true)
    public Page<SelfReportResponse> listSelfReports(Optional<String> status, Optional<String> reference,
                                                    Optional<String> service, Pageable pageable,
                                                    String username) {
        UserEntity account = findAccount(username);
        boolean admin = isAdmin(account);
        List<SelfReportEntity> reports = admin
                ? selfReportRepository.findAllByOrderByRequestedAtDesc()
                : selfReportRepository.findByCustomerOrderByRequestedAtDesc(linkedCustomer(account));

        SelfReportStatus statusFilter = status.map(this::parseStatus).orElse(null);
        Product serviceFilter = service.map(this::parseService).orElse(null);
        String referenceFilter = admin ? reference.map(String::trim).filter(value -> !value.isBlank()).orElse(null) : null;

        List<SelfReportResponse> result = new ArrayList<>();
        for (SelfReportEntity report : reports) {
            if (statusFilter != null && report.getStatus() != statusFilter) {
                continue;
            }
            if (serviceFilter != null && report.getService() != serviceFilter) {
                continue;
            }
            if (referenceFilter != null && !report.getCustomer().getReference().equals(referenceFilter)) {
                continue;
            }
            result.add(toSelfReportResponse(report));
        }
        return page(result, pageable);
    }

    @Transactional
    public SelfReportResponse acceptSelfReport(String id, String adminUsername) {
        SelfReportEntity report = findReport(id);
        if (report.getStatus() != SelfReportStatus.PENDING) {
            throw new SelfReportStateException("Self report has already been processed");
        }
        ensureNoReadingExists(report);
        validateCumulativeReading(report.getCustomer(), report.getService(), report.getReadingDate(), report.getAmount());

        UserEntity administrator = findAccount(adminUsername);
        ReadingEntity reading = new ReadingEntity(
                null,
                report.getCustomer(),
                report.getService(),
                toReadingDateTime(report.getReadingDate()),
                report.getAmount(),
                false,
                true,
                ReadingSource.SELF_REPORTED,
                null
        );
        readingRepository.save(reading);

        report.setStatus(SelfReportStatus.ACCEPTED);
        report.setReviewedAt(OffsetDateTime.now(clock));
        report.setReviewedBy(administrator);
        auditService.record("SELF_REPORT_ACCEPTED", adminUsername, "READINGS",
                "reportId=%s reference=%s service=%s date=%s admin=%s"
                        .formatted(report.getId(), report.getCustomer().getReference(),
                                serviceName(report.getService()), report.getReadingDate(), adminUsername));
        return toSelfReportResponse(report);
    }

    @Transactional
    public SelfReportResponse denySelfReport(String id, String adminUsername) {
        SelfReportEntity report = findReport(id);
        if (report.getStatus() != SelfReportStatus.PENDING) {
            throw new SelfReportStateException("Self report has already been processed");
        }

        UserEntity administrator = findAccount(adminUsername);
        report.setStatus(SelfReportStatus.DECLINED);
        report.setReviewedAt(OffsetDateTime.now(clock));
        report.setReviewedBy(administrator);
        auditService.record("SELF_REPORT_DECLINED", adminUsername, "READINGS",
                "reportId=%s reference=%s service=%s date=%s admin=%s"
                        .formatted(report.getId(), report.getCustomer().getReference(),
                                serviceName(report.getService()), report.getReadingDate(), adminUsername));
        return toSelfReportResponse(report);
    }

    @Transactional(readOnly = true)
    public Page<ReadingResponse> listReadings(Optional<String> reference, Optional<String> service,
                                              Optional<String> source, Optional<LocalDate> from,
                                              Optional<LocalDate> to, Pageable pageable,
                                              String username) {
        UserEntity account = findAccount(username);
        boolean admin = isAdmin(account);
        List<ReadingEntity> readings = admin
                ? readingRepository.findAllByOrderByDateTimeDesc()
                : readingRepository.findByCustomerOrderByDateTimeDesc(linkedCustomer(account));

        Product serviceFilter = service.map(this::parseService).orElse(null);
        ReadingSource sourceFilter = source.map(this::parseSource).orElse(null);
        String referenceFilter = admin ? reference.map(String::trim).filter(value -> !value.isBlank()).orElse(null) : null;

        List<ReadingResponse> result = new ArrayList<>();
        for (ReadingEntity reading : readings) {
            LocalDate readingDate = readingDate(reading);
            if (referenceFilter != null && !reading.getCustomer().getReference().equals(referenceFilter)) {
                continue;
            }
            if (serviceFilter != null && reading.getProduct() != serviceFilter) {
                continue;
            }
            if (sourceFilter != null && reading.getSource() != sourceFilter) {
                continue;
            }
            if (from.isPresent() && readingDate.isBefore(from.get())) {
                continue;
            }
            if (to.isPresent() && readingDate.isAfter(to.get())) {
                continue;
            }
            result.add(toReadingResponse(reading));
        }
        return page(result, pageable);
    }

    private UserEntity findAccount(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated account was not found"));
    }

    private CustomerEntity linkedCustomer(UserEntity account) {
        if (account.getCustomer() == null) {
            throw new IllegalArgumentException("Authenticated user is not linked to a customer");
        }
        return account.getCustomer();
    }

    private boolean isAdmin(UserEntity account) {
        return "ADMIN".equals(account.getRole());
    }

    private SelfReportEntity findReport(String id) {
        return selfReportRepository.findById(id)
                .orElseThrow(() -> new SelfReportNotFoundException(id));
    }

    private void ensureNoReadingExists(SelfReportEntity report) {
        OffsetDateTime start = toReadingDateTime(report.getReadingDate());
        OffsetDateTime end = toReadingDateTime(report.getReadingDate().plusDays(1));
        boolean exists = readingRepository.existsByCustomerAndProductAndDateTimeGreaterThanEqualAndDateTimeLessThan(
                report.getCustomer(), report.getService(), start, end);
        if (exists) {
            throw new SelfReportStateException("A reading already exists for this customer, service and date");
        }
    }

    private void validateCumulativeReading(CustomerEntity customer, Product service, LocalDate date, BigDecimal amount) {
        OffsetDateTime readingDateTime = toReadingDateTime(date);
        ReadingEntity previous = readingRepository
                .findFirstByCustomerAndProductAndDateTimeBeforeOrderByDateTimeDesc(customer, service, readingDateTime)
                .orElse(null);
        if (previous != null && amount.compareTo(previous.getLastReading()) < 0) {
            throw new IllegalArgumentException(
                    "Reading amount cannot be lower than the previous cumulative reading. Latest earlier reading: "
                            + previous.getLastReading() + "; submitted reading: " + amount);
        }
    }

    private void validateSelfReportDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock.withZone(SOFIA_ZONE));
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Reading date must not be in the future");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Reading amount must not be negative");
        }
    }

    private OffsetDateTime toReadingDateTime(LocalDate date) {
        return date.atStartOfDay(SOFIA_ZONE).toOffsetDateTime();
    }

    private Product parseService(String value) {
        if (value == null) {
            throw new IllegalArgumentException("service is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "gas" -> Product.GAS;
            case "elec" -> Product.ELECT;
            default -> throw new IllegalArgumentException("service must be gas or elec");
        };
    }

    private SelfReportStatus parseStatus(String value) {
        if ("DENIED".equalsIgnoreCase(value.trim())) {
            return SelfReportStatus.DECLINED;
        }
        try {
            return SelfReportStatus.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("status must be PENDING, ACCEPTED or DENIED");
        }
    }

    private ReadingSource parseSource(String value) {
        try {
            return ReadingSource.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("source must be IMPORTED or SELF_REPORTED");
        }
    }

    private String serviceName(Product service) {
        return service == Product.GAS ? "gas" : "elec";
    }

    private SelfReportResponse toSelfReportResponse(SelfReportEntity report) {
        String reviewedBy = report.getReviewedBy() == null ? null : report.getReviewedBy().getUsername();
        return new SelfReportResponse(
                report.getId(),
                report.getCustomer().getReference(),
                report.getCustomer().getName(),
                report.getReadingDate(),
                serviceName(report.getService()),
                report.getAmount(),
                statusName(report.getStatus()),
                report.getRequestedAt(),
                report.getReviewedAt(),
                reviewedBy
        );
    }

    private String statusName(SelfReportStatus status) {
        return status == SelfReportStatus.DECLINED ? "DENIED" : status.name();
    }

    private ReadingResponse toReadingResponse(ReadingEntity reading) {
        return new ReadingResponse(
                reading.getId(),
                reading.getCustomer().getReference(),
                reading.getCustomer().getName(),
                reading.getDateTime(),
                serviceName(reading.getProduct()),
                reading.getLastReading(),
                reading.getSource() == ReadingSource.SELF_REPORTED,
                reading.isInvoiced()
        );
    }

    private LocalDate readingDate(ReadingEntity reading) {
        return reading.getDateTime().atZoneSameInstant(SOFIA_ZONE).toLocalDate();
    }

    private <T> Page<T> page(List<T> content, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(content);
        }
        int start = Math.min((int) pageable.getOffset(), content.size());
        int end = Math.min(start + pageable.getPageSize(), content.size());
        return new PageImpl<>(content.subList(start, end), pageable, content.size());
    }
}
