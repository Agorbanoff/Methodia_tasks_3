package com.methodia.minibilling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.methodia.minibilling.controller.dto.report.AuditLogResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.PageRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void recordAppendsJsonLinesToAuditFile() throws Exception {
        Path auditFile = tempDir.resolve("logs").resolve("audit.log");
        AuditService auditService = new AuditService(objectMapper(), fixedClock(), auditFile);

        auditService.record("LOGIN", "admin", "AUTH", "User logged in");
        auditService.record("LOGOUT", "admin", "AUTH", "User logged out");

        var lines = Files.readAllLines(auditFile, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(2);
        assertThat(lines.getFirst()).contains("\"username\":\"admin\"");
        assertThat(lines.getFirst()).contains("\"action\":\"LOGIN\"");
        assertThat(lines.getFirst()).contains("\"module\":\"AUTH\"");
    }

    @Test
    void malformedLinesDoNotCrashAuditReading() throws Exception {
        Path auditFile = tempDir.resolve("logs").resolve("audit.log");
        Files.createDirectories(auditFile.getParent());
        Files.writeString(auditFile, """
                not-json
                {"id":"id-1","occurredAt":"2026-08-02T16:00:00+03:00","username":"admin","action":"LOGIN","module":"AUTH","description":"User logged in"}
                """, StandardCharsets.UTF_8);
        AuditService auditService = new AuditService(objectMapper(), fixedClock(), auditFile);

        var page = auditService.list(PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        AuditLogResponse response = page.getContent().getFirst();
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.action()).isEqualTo("LOGIN");
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-08-02T13:00:00Z"), ZoneId.of("Europe/Sofia"));
    }
}
