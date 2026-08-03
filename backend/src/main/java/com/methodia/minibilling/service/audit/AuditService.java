package com.methodia.minibilling.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.methodia.minibilling.controller.dto.report.AuditLogResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final Object writeLock = new Object();
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Path auditLogPath;

    public AuditService(ObjectMapper objectMapper, Clock clock,
                        @Value("${app.audit.log-file:logs/audit.log}") Path auditLogPath) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.auditLogPath = auditLogPath;
    }

    public void record(String action, String username, String module, String description) {
        AuditLogResponse event = new AuditLogResponse(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(clock),
                username,
                action,
                module,
                description
        );
        synchronized (writeLock) {
            try {
                Path parent = auditLogPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(auditLogPath, objectMapper.writeValueAsString(event) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to write audit log", exception);
            }
        }
    }

    public Page<AuditLogResponse> list(Pageable pageable) {
        if (!Files.exists(auditLogPath)) {
            return Page.empty(pageable);
        }
        try {
            List<AuditLogResponse> entries = Files.readAllLines(auditLogPath, StandardCharsets.UTF_8).stream()
                    .map(this::parseLine)
                    .flatMap(List::stream)
                    .sorted(Comparator.comparing(AuditLogResponse::occurredAt).reversed())
                    .toList();
            int start = Math.min((int) pageable.getOffset(), entries.size());
            int end = Math.min(start + pageable.getPageSize(), entries.size());
            return new PageImpl<>(entries.subList(start, end), pageable, entries.size());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read audit log", exception);
        }
    }

    private List<AuditLogResponse> parseLine(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(line, AuditLogResponse.class));
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
