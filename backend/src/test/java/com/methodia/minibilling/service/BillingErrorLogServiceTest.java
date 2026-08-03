package com.methodia.minibilling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.methodia.minibilling.model.error.ErrorSeverity;
import com.methodia.minibilling.service.audit.BillingErrorLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingErrorLogServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void recordWritesErrorLogToLogFile() throws Exception {
        Path logFile = tempDir.resolve("error.log");
        BillingErrorLogService service = service(logFile);

        service.record("MISSING_TARIFF", "No tariff", "DUMMY-1001", "BILLING", ErrorSeverity.ERROR);

        assertThat(logFile).exists();
        String logLine = Files.readString(logFile, StandardCharsets.UTF_8);
        assertThat(logLine).contains("\"errorType\":\"MISSING_TARIFF\"");
        assertThat(logLine).contains("\"customerId\":\"DUMMY-1001\"");
        assertThat(logLine).contains("\"severity\":\"ERROR\"");
    }

    @Test
    void defaultSortUsesOccurredAtDescending() {
        Path logFile = tempDir.resolve("error.log");
        BillingErrorLogService service = service(logFile);
        service.record("OLDER", "Older error", "DUMMY-1001", "BILLING", ErrorSeverity.ERROR);
        service.record("NEWER", "Newer error", "DUMMY-1001", "BILLING", ErrorSeverity.WARNING);

        var page = service.list(PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting("errorType").containsExactly("OLDER", "NEWER");
    }

    @Test
    void occurredAtSortDoesNotUseEntityProperties() {
        Path logFile = tempDir.resolve("error.log");
        BillingErrorLogService service = service(logFile);

        service.record("MISSING_TARIFF", "No tariff", "DUMMY-1001", "BILLING", ErrorSeverity.ERROR);

        var page = service.list(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "occurredAt")));

        assertThat(page.getContent()).singleElement()
                .satisfies(log -> assertThat(log.occurredAt()).isNotNull());
    }

    @Test
    void unsupportedSortPropertyReturnsBadRequestThroughGlobalHandler() {
        BillingErrorLogService service = service(tempDir.resolve("error.log"));

        assertThatThrownBy(() -> service.list(PageRequest.of(0, 20, Sort.by("unknownFrontendField"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported error log sort property");
    }

    private BillingErrorLogService service(Path logFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new BillingErrorLogService(objectMapper, fixedClock(), logFile);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC);
    }
}
