package com.methodia.minibilling.service;

import com.methodia.minibilling.model.BillingRunStatus;
import com.methodia.minibilling.persistence.entity.BillingRunItemEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class BillingRunProcessor {

    private final BillingRunItemProcessor billingRunItemProcessor;

    public BillingRunProcessor(BillingRunItemProcessor billingRunItemProcessor) {
        this.billingRunItemProcessor = billingRunItemProcessor;
    }

    @Async
    public void processPending(String runId) {
        try {
            while (billingRunItemProcessor.currentStatus(runId) == BillingRunStatus.IN_PROGRESS) {
                BillingRunItemEntity item = billingRunItemProcessor.nextPendingItem(runId);
                if (item == null) {
                    billingRunItemProcessor.completeRunIfFinished(runId);
                    return;
                }
                billingRunItemProcessor.processItem(item.getId());
            }
        } catch (RuntimeException exception) {
            billingRunItemProcessor.failRun(runId, exception);
        }
    }
}
