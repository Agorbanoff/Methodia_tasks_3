package com.methodia.minibilling.service;

import com.methodia.minibilling.controller.dto.report.BillingReportResponse;
import com.methodia.minibilling.controller.dto.billing.BillingRunRequest;
import com.methodia.minibilling.controller.dto.billing.BillingRunResponse;
import com.methodia.minibilling.exception.BillingRunStateException;
import com.methodia.minibilling.model.BillingRunItemStatus;
import com.methodia.minibilling.model.BillingRunStatus;
import com.methodia.minibilling.persistence.entity.BillingRunEntity;
import com.methodia.minibilling.persistence.entity.BillingRunItemEntity;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.BillingRunItemRepository;
import com.methodia.minibilling.repository.BillingRunRepository;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;

@Service
public class BillingRunService {

    private static final String ALL_CUSTOMERS = "all";
    private static final List<BillingRunStatus> ACTIVE_RUN_STATUSES = List.of(BillingRunStatus.IN_PROGRESS);
    private static final List<BillingRunItemStatus> ACTIVE_ITEM_STATUSES = List.of(
            BillingRunItemStatus.PENDING,
            BillingRunItemStatus.PROCESSING
    );

    private final BillingRunRepository billingRunRepository;
    private final BillingRunItemRepository billingRunItemRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final TariffSnapshotService tariffSnapshotService;
    private final BillingRunProcessor billingRunProcessor;
    private final Clock clock;

    public BillingRunService(BillingRunRepository billingRunRepository,
                             BillingRunItemRepository billingRunItemRepository,
                             UserRepository userRepository,
                             CustomerRepository customerRepository,
                             AuditService auditService,
                             TariffSnapshotService tariffSnapshotService,
                             BillingRunProcessor billingRunProcessor,
                             Clock clock) {
        this.billingRunRepository = billingRunRepository;
        this.billingRunItemRepository = billingRunItemRepository;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
        this.tariffSnapshotService = tariffSnapshotService;
        this.billingRunProcessor = billingRunProcessor;
        this.clock = clock;
    }

    @Transactional
    public synchronized BillingRunResponse start(BillingRunRequest request, String actorUsername) {
        LocalDate periodStart = LocalDate.parse(request.startDate());
        LocalDate periodEnd = LocalDate.parse(request.endDate());
        validatePeriod(periodStart, periodEnd);

        UserEntity actor = findActor(actorUsername);
        List<CustomerEntity> customers = resolveCustomers(request.reference());
        ensureNoActiveRunForCustomers(customers, periodStart, periodEnd);

        OffsetDateTime now = OffsetDateTime.now(clock);
        BillingRunEntity run = billingRunRepository.save(new BillingRunEntity(
                periodStart,
                periodEnd,
                actor,
                normalizedReference(request.reference()),
                now
        ));
        run.setFrozenTariffVersion("RUN-" + run.getId());
        run.setStatus(BillingRunStatus.IN_PROGRESS);
        run.setTotalRecords(customers.size());
        run.setUpdatedAt(now);

        List<BillingRunItemEntity> items = new ArrayList<>();
        for (CustomerEntity customer : customers) {
            String snapshot = tariffSnapshotService.createSnapshot(customer);
            items.add(new BillingRunItemEntity(run, customer, snapshot));
        }
        billingRunItemRepository.saveAll(items);
        auditService.record("BILLING_RUN_STARTED", actorUsername, "BILLING", "Started billing run " + run.getId());

        processAfterCommit(run.getId());
        return toResponse(run);
    }

    @Transactional
    public BillingRunResponse stop(String runId, String actorUsername) {
        BillingRunEntity run = findRun(runId);
        if (run.getStatus() != BillingRunStatus.IN_PROGRESS) {
            throw new BillingRunStateException("Only an IN_PROGRESS billing run can be stopped");
        }
        run.setStatus(BillingRunStatus.PAUSED);
        run.setUpdatedAt(OffsetDateTime.now(clock));
        updateCounters(run);
        auditService.record("BILLING_RUN_PAUSED", actorUsername, "BILLING", "Paused billing run " + runId);
        return toResponse(run);
    }

    @Transactional
    public BillingRunResponse resume(String runId, String actorUsername) {
        BillingRunEntity run = findRun(runId);
        if (run.getStatus() != BillingRunStatus.PAUSED) {
            throw new BillingRunStateException("Only a PAUSED billing run can be resumed");
        }
        run.setStatus(BillingRunStatus.IN_PROGRESS);
        run.setEndedAt(null);
        run.setUpdatedAt(OffsetDateTime.now(clock));
        updateCounters(run);
        auditService.record("BILLING_RUN_RESUMED", actorUsername, "BILLING", "Resumed billing run " + runId);

        processAfterCommit(run.getId());
        return toResponse(run);
    }

    @Transactional
    public BillingRunResponse restart(String runId, String actorUsername) {
        BillingRunEntity oldRun = findRun(runId);
        if (oldRun.getStatus() != BillingRunStatus.FAILED && oldRun.getStatus() != BillingRunStatus.COMPLETED) {
            throw new BillingRunStateException("Only a FAILED or COMPLETED billing run can be restarted");
        }

        BillingRunRequest request = new BillingRunRequest(
                oldRun.getPeriodStart().toString(),
                oldRun.getPeriodEnd().toString(),
                oldRun.getReference()
        );
        BillingRunResponse newRun = start(request, actorUsername);
        auditService.record("BILLING_RUN_RESTARTED", actorUsername, "BILLING",
                "Restarted billing run " + runId + " as " + newRun.id());
        return newRun;
    }

    @Transactional(readOnly = true)
    public BillingRunResponse get(String runId) {
        return toResponse(findRun(runId));
    }

    @Transactional(readOnly = true)
    public Page<BillingRunResponse> list(Pageable pageable) {
        return billingRunRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Transactional
    public BillingReportResponse report(String runId, String actorUsername) {
        BillingRunEntity run = findRun(runId);
        BillingRunResponse runResponse = toResponse(run);
        int successfulInvoices = billingRunItemRepository.countByBillingRunAndStatus(run, BillingRunItemStatus.PROCESSED);
        int warningInvoices = billingRunItemRepository.countByBillingRunAndStatus(run, BillingRunItemStatus.WARNING);
        int failedInvoices = billingRunItemRepository.countByBillingRunAndStatus(run, BillingRunItemStatus.FAILED);
        long durationSeconds = processingDurationSeconds(run);
        auditService.record("REPORT_GENERATED", actorUsername, "REPORTS", "Generated billing report for run " + runId);
        return new BillingReportResponse(
                run.getId(),
                run.getStatus(),
                successfulInvoices + warningInvoices + failedInvoices,
                successfulInvoices,
                failedInvoices,
                warningInvoices,
                durationSeconds,
                failureSummary(run),
                runResponse
        );
    }

    private UserEntity findActor(String actorUsername) {
        return userRepository.findByUsername(actorUsername)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated administrator was not found"));
    }

    private List<CustomerEntity> resolveCustomers(String reference) {
        String normalizedReference = normalizedReference(reference);
        if (ALL_CUSTOMERS.equalsIgnoreCase(normalizedReference)) {
            return customerRepository.findAll().stream()
                    .sorted(java.util.Comparator.comparing(CustomerEntity::getReference))
                    .toList();
        }

        CustomerEntity customer = customerRepository.findByReference(normalizedReference)
                .orElseThrow(() -> new IllegalArgumentException("Customer reference does not exist: " + normalizedReference));
        return List.of(customer);
    }

    private void ensureNoActiveRunForCustomers(List<CustomerEntity> customers, LocalDate periodStart, LocalDate periodEnd) {
        for (CustomerEntity customer : customers) {
            boolean active = billingRunItemRepository.existsActiveItemForCustomerAndPeriod(
                    customer,
                    periodStart,
                    periodEnd,
                    ACTIVE_RUN_STATUSES,
                    ACTIVE_ITEM_STATUSES
            );
            if (active) {
                throw new BillingRunStateException("Customer " + customer.getReference()
                        + " already has an active billing run for this period");
            }
        }
    }

    private void validatePeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
        if (!YearMonth.from(periodStart).equals(YearMonth.from(periodEnd))) {
            throw new IllegalArgumentException("Billing runs must stay within one calendar month");
        }
    }

    private String normalizedReference(String reference) {
        return reference == null ? "" : reference.trim();
    }

    private BillingRunEntity findRun(String runId) {
        return billingRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Billing run not found: " + runId));
    }

    private void processAfterCommit(String runId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                billingRunProcessor.processPending(runId);
            }
        });
    }

    private void updateCounters(BillingRunEntity run) {
        int processed = billingRunItemRepository.countByBillingRunAndStatusIn(
                run,
                List.of(BillingRunItemStatus.PROCESSED, BillingRunItemStatus.WARNING)
        );
        run.setProcessedRecords(processed);
        run.setFailedRecords(billingRunItemRepository.countByBillingRunAndStatus(run, BillingRunItemStatus.FAILED));
        run.setWarningRecords(billingRunItemRepository.countByBillingRunAndStatus(run, BillingRunItemStatus.WARNING));
    }

    private BillingRunResponse toResponse(BillingRunEntity run) {
        String startedBy = run.getStartedBy() == null ? null : run.getStartedBy().getUsername();
        return new BillingRunResponse(
                run.getId(),
                run.getPeriodStart().toString(),
                run.getPeriodEnd().toString(),
                run.getStatus(),
                run.getStartedAt(),
                run.getEndedAt(),
                startedBy,
                run.getProcessedRecords(),
                run.getFailedRecords(),
                run.getWarningRecords(),
                run.getTotalRecords(),
                run.getFrozenTariffVersion()
        );
    }

    private long processingDurationSeconds(BillingRunEntity run) {
        if (run.getStartedAt() == null) {
            return 0;
        }
        OffsetDateTime end = run.getEndedAt() == null ? OffsetDateTime.now(clock) : run.getEndedAt();
        return Math.max(0, Duration.between(run.getStartedAt(), end).getSeconds());
    }

    private String failureSummary(BillingRunEntity run) {
        List<BillingRunItemEntity> failedItems = billingRunItemRepository
                .findByBillingRunAndStatusOrderByCustomerReferenceAsc(run, BillingRunItemStatus.FAILED);
        if (failedItems.isEmpty()) {
            return "No failures";
        }

        Map<String, Integer> counts = new TreeMap<>();
        for (BillingRunItemEntity item : failedItems) {
            String reason = failureReason(item);
            counts.put(reason, counts.getOrDefault(reason, 0) + 1);
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            parts.add(entry.getKey() + ": " + entry.getValue());
        }
        return String.join("; ", parts);
    }

    private String failureReason(BillingRunItemEntity item) {
        String message = item.getErrorMessage();
        if (message == null || message.isBlank()) {
            return "UNKNOWN";
        }
        String trimmed = message.trim();
        int separator = trimmed.indexOf(':');
        if (separator > 0) {
            return toFailureCode(trimmed.substring(0, separator));
        }
        return toFailureCode(trimmed);
    }

    private String toFailureCode(String text) {
        StringBuilder code = new StringBuilder();
        for (char character : text.toCharArray()) {
            if (Character.isLetterOrDigit(character)) {
                code.append(Character.toUpperCase(character));
            } else if (!code.isEmpty() && code.charAt(code.length() - 1) != '_') {
                code.append('_');
            }
        }
        while (!code.isEmpty() && code.charAt(code.length() - 1) == '_') {
            code.deleteCharAt(code.length() - 1);
        }
        return code.isEmpty() ? "UNKNOWN" : code.toString();
    }
}
