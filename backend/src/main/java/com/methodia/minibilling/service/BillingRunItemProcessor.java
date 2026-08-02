package com.methodia.minibilling.service;

import com.methodia.minibilling.model.BillingRunItemStatus;
import com.methodia.minibilling.model.BillingRunStatus;
import com.methodia.minibilling.model.ErrorSeverity;
import com.methodia.minibilling.persistence.entity.BillingRunEntity;
import com.methodia.minibilling.persistence.entity.BillingRunItemEntity;
import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import com.methodia.minibilling.repository.BillingRunItemRepository;
import com.methodia.minibilling.repository.BillingRunRepository;
import com.methodia.minibilling.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class BillingRunItemProcessor {

    private final BillingRunRepository billingRunRepository;
    private final BillingRunItemRepository billingRunItemRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingService billingService;
    private final TariffSnapshotService tariffSnapshotService;
    private final BillingErrorLogService billingErrorLogService;
    private final AuditService auditService;
    private final Clock clock;

    public BillingRunItemProcessor(BillingRunRepository billingRunRepository,
                                   BillingRunItemRepository billingRunItemRepository,
                                   InvoiceRepository invoiceRepository,
                                   BillingService billingService,
                                   TariffSnapshotService tariffSnapshotService,
                                   BillingErrorLogService billingErrorLogService,
                                   AuditService auditService,
                                   Clock clock) {
        this.billingRunRepository = billingRunRepository;
        this.billingRunItemRepository = billingRunItemRepository;
        this.invoiceRepository = invoiceRepository;
        this.billingService = billingService;
        this.tariffSnapshotService = tariffSnapshotService;
        this.billingErrorLogService = billingErrorLogService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BillingRunStatus currentStatus(String runId) {
        return findRun(runId).getStatus();
    }

    @Transactional(readOnly = true)
    public BillingRunItemEntity nextPendingItem(String runId) {
        return billingRunItemRepository
                .findFirstByBillingRunIdAndStatusOrderByCustomerReferenceAsc(runId, BillingRunItemStatus.PENDING)
                .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processItem(String itemId) {
        BillingRunItemEntity item = billingRunItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Billing run item not found: " + itemId));
        BillingRunEntity run = item.getBillingRun();
        if (run.getStatus() != BillingRunStatus.IN_PROGRESS || item.getStatus() != BillingRunItemStatus.PENDING) {
            return;
        }

        item.setStatus(BillingRunItemStatus.PROCESSING);
        billingRunItemRepository.saveAndFlush(item);

        try {
            YearMonth invoiceMonth = YearMonth.from(run.getPeriodStart());
            var prices = tariffSnapshotService.readSnapshot(item.getTariffSnapshot());
            InvoiceGenerationResult result = billingService.generateInvoiceForCustomer(item.getCustomer(), invoiceMonth, prices);
            InvoiceEntity invoice = invoiceRepository
                    .findByCustomerAndBillingYearAndBillingMonth(
                            item.getCustomer(),
                            invoiceMonth.getYear(),
                            invoiceMonth.getMonthValue()
                    )
                    .orElse(null);
            item.setInvoice(invoice);
            if (result.warnings().isEmpty()) {
                item.setStatus(BillingRunItemStatus.PROCESSED);
                item.setSeverity(null);
                item.setErrorMessage(null);
            } else {
                item.setStatus(BillingRunItemStatus.WARNING);
                item.setSeverity(ErrorSeverity.WARNING);
                item.setErrorMessage(shortMessage(String.join("; ", result.warnings())));
                for (String warning : result.warnings()) {
                    billingErrorLogService.record("UNUSUAL_CONSUMPTION", warning,
                            item.getCustomer().getReference(), "BillingRunItemProcessor", ErrorSeverity.WARNING);
                }
            }
        } catch (RuntimeException exception) {
            item.setStatus(BillingRunItemStatus.FAILED);
            item.setSeverity(ErrorSeverity.ERROR);
            item.setErrorMessage(shortMessage(exception.getMessage()));
            billingErrorLogService.record(exception.getClass().getSimpleName(), exception.getMessage(),
                    item.getCustomer().getReference(), "BillingRunItemProcessor");
        }

        item.setProcessedAt(OffsetDateTime.now(clock));
        updateCounters(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeRunIfFinished(String runId) {
        BillingRunEntity run = findRun(runId);
        if (run.getStatus() != BillingRunStatus.IN_PROGRESS) {
            return;
        }
        int pending = billingRunItemRepository.countByBillingRunAndStatus(run, BillingRunItemStatus.PENDING);
        int processing = billingRunItemRepository.countByBillingRunAndStatus(run, BillingRunItemStatus.PROCESSING);
        if (pending > 0 || processing > 0) {
            return;
        }

        updateCounters(run);
        run.setStatus(BillingRunStatus.COMPLETED);
        run.setEndedAt(OffsetDateTime.now(clock));
        run.setUpdatedAt(OffsetDateTime.now(clock));
        auditService.record("BILLING_RUN_COMPLETED", startedByUsername(run), "BILLING",
                "Completed billing run " + run.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRun(String runId, RuntimeException exception) {
        BillingRunEntity run = findRun(runId);
        run.setStatus(BillingRunStatus.FAILED);
        run.setEndedAt(OffsetDateTime.now(clock));
        run.setUpdatedAt(OffsetDateTime.now(clock));
        updateCounters(run);
        billingErrorLogService.record(exception.getClass().getSimpleName(), exception.getMessage(),
                null, "BillingRunProcessor", ErrorSeverity.CRITICAL);
        auditService.record("BILLING_RUN_FAILED", startedByUsername(run), "BILLING",
                "Failed billing run " + runId);
    }

    private BillingRunEntity findRun(String runId) {
        return billingRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Billing run not found: " + runId));
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

    private String shortMessage(String message) {
        if (message == null || message.isBlank()) {
            return "No details";
        }
        if (message.length() <= 1000) {
            return message;
        }
        return message.substring(0, 1000);
    }

    private String startedByUsername(BillingRunEntity run) {
        return run.getStartedBy() == null ? null : run.getStartedBy().getUsername();
    }
}
