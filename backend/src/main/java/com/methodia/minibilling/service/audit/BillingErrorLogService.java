package com.methodia.minibilling.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.methodia.minibilling.controller.dto.report.BillingErrorLogResponse;
import com.methodia.minibilling.model.error.ErrorSeverity;
import com.methodia.minibilling.model.error.ErrorStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingErrorLogService {

    private static final String DEFAULT_SORT_PROPERTY = "occurredAt";
    private static final Map<String, Comparator<BillingErrorLogResponse>> SORT_PROPERTIES = sortProperties();

    private final Object writeLock = new Object();
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Path errorLogPath;

    public BillingErrorLogService(ObjectMapper objectMapper, Clock clock,
                                  @Value("${app.error.log-file:logs/error.log}") Path errorLogPath) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.errorLogPath = errorLogPath;
    }

    public void record(String type, String description, String customerId, String module) {
        record(type, description, customerId, module, ErrorSeverity.ERROR);
    }

    public void record(String type, String description, String customerId, String module, ErrorSeverity severity) {
        BillingErrorLogResponse event = new BillingErrorLogResponse(
                OffsetDateTime.now(clock),
                UUID.randomUUID().toString(),
                type,
                customerId,
                module,
                severity,
                description,
                ErrorStatus.OPEN
        );
        synchronized (writeLock) {
            try {
                Path parent = errorLogPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(errorLogPath, objectMapper.writeValueAsString(event) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to write error log", exception);
            }
        }
    }

    public Page<BillingErrorLogResponse> list(Pageable pageable) {
        Comparator<BillingErrorLogResponse> comparator = comparator(pageable.getSort());
        if (!Files.exists(errorLogPath)) {
            return Page.empty(pageable);
        }
        try {
            List<BillingErrorLogResponse> entries = Files.readAllLines(errorLogPath, StandardCharsets.UTF_8).stream()
                    .map(this::parseLine)
                    .flatMap(List::stream)
                    .sorted(comparator)
                    .toList();
            int start = Math.min((int) pageable.getOffset(), entries.size());
            int end = Math.min(start + pageable.getPageSize(), entries.size());
            return new PageImpl<>(entries.subList(start, end), pageable, entries.size());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read error log", exception);
        }
    }

    private List<BillingErrorLogResponse> parseLine(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(line, BillingErrorLogResponse.class));
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private Comparator<BillingErrorLogResponse> comparator(Sort sort) {
        if (sort.isUnsorted()) {
            return SORT_PROPERTIES.get(DEFAULT_SORT_PROPERTY).reversed();
        }
        if (sort.stream().count() != 1) {
            throw new IllegalArgumentException("Unsupported error log sort");
        }
        Sort.Order order = sort.iterator().next();
        Comparator<BillingErrorLogResponse> comparator = SORT_PROPERTIES.get(order.getProperty());
        if (comparator == null) {
            throw new IllegalArgumentException("Unsupported error log sort property: " + order.getProperty());
        }
        if (order.getDirection() == Sort.Direction.DESC) {
            return comparator.reversed();
        }
        return comparator;
    }

    private static Map<String, Comparator<BillingErrorLogResponse>> sortProperties() {
        Map<String, Comparator<BillingErrorLogResponse>> properties = new HashMap<>();
        properties.put("occurredAt", Comparator.comparing(BillingErrorLogResponse::occurredAt));
        properties.put("errorId", Comparator.comparing(BillingErrorLogResponse::errorId));
        properties.put("errorType", Comparator.comparing(BillingErrorLogResponse::errorType));
        properties.put("customerId", Comparator.comparing(BillingErrorLogResponse::customerId,
                Comparator.nullsLast(String::compareTo)));
        properties.put("module", Comparator.comparing(BillingErrorLogResponse::module));
        properties.put("severity", Comparator.comparing(BillingErrorLogResponse::severity));
        properties.put("description", Comparator.comparing(BillingErrorLogResponse::description,
                Comparator.nullsLast(String::compareTo)));
        properties.put("status", Comparator.comparing(BillingErrorLogResponse::status));
        return properties;
    }
}
