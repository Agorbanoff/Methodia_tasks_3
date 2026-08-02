package com.methodia.minibilling.controller.dto.report;

import java.time.OffsetDateTime;

public record AuditLogResponse(String id, OffsetDateTime occurredAt, String username, String action, String module,
                               String description) {
}
