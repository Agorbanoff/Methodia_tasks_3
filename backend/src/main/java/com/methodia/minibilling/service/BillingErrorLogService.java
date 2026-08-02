package com.methodia.minibilling.service;

import com.methodia.minibilling.controller.dto.report.BillingErrorLogResponse;
import com.methodia.minibilling.model.ErrorSeverity;
import com.methodia.minibilling.model.ErrorStatus;
import com.methodia.minibilling.persistence.entity.BillingErrorLogEntity;
import com.methodia.minibilling.repository.BillingErrorLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class BillingErrorLogService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Map<String, String> SORT_PROPERTIES = sortProperties();

    private final BillingErrorLogRepository billingErrorLogRepository;
    private final Clock clock;

    public BillingErrorLogService(BillingErrorLogRepository billingErrorLogRepository, Clock clock) {
        this.billingErrorLogRepository = billingErrorLogRepository;
        this.clock = clock;
    }

    @Transactional
    public void record(String type, String description, String customerId, String module) {
        record(type, description, customerId, module, ErrorSeverity.ERROR);
    }

    @Transactional
    public void record(String type, String description, String customerId, String module, ErrorSeverity severity) {
        billingErrorLogRepository.save(new BillingErrorLogEntity(
                type, description, customerId, module, severity, ErrorStatus.OPEN, OffsetDateTime.now(clock)));
    }

    @Transactional(readOnly = true)
    public Page<BillingErrorLogResponse> list(Pageable pageable) {
        return billingErrorLogRepository.findAll(toEntityPageable(pageable)).map(this::toResponse);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOlderThan30Days() {
        billingErrorLogRepository.deleteByCreatedAtBefore(OffsetDateTime.now(clock).minusDays(30));
    }

    private BillingErrorLogResponse toResponse(BillingErrorLogEntity entity) {
        return new BillingErrorLogResponse(
                entity.getCreatedAt(),
                entity.getId(),
                entity.getType(),
                entity.getCustomerId(),
                entity.getModule(),
                entity.getSeverity(),
                entity.getDescription(),
                entity.getStatus()
        );
    }

    private Pageable toEntityPageable(Pageable pageable) {
        Sort sort = toEntitySort(pageable.getSort());
        if (pageable.isUnpaged()) {
            return Pageable.unpaged(sort);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private Sort toEntitySort(Sort requestedSort) {
        if (requestedSort.isUnsorted()) {
            return DEFAULT_SORT;
        }

        Sort entitySort = Sort.unsorted();
        for (Sort.Order order : requestedSort) {
            String entityProperty = SORT_PROPERTIES.get(order.getProperty());
            if (entityProperty == null) {
                throw new IllegalArgumentException("Unsupported error log sort property: " + order.getProperty());
            }
            entitySort = entitySort.and(Sort.by(new Sort.Order(order.getDirection(), entityProperty)));
        }
        return entitySort;
    }

    private static Map<String, String> sortProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("occurredAt", "createdAt");
        properties.put("errorId", "id");
        properties.put("errorType", "type");
        properties.put("customerId", "customerId");
        properties.put("module", "module");
        properties.put("severity", "severity");
        properties.put("description", "description");
        properties.put("status", "status");
        return properties;
    }
}
