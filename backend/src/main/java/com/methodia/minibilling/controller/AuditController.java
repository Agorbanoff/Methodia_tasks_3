package com.methodia.minibilling.controller;

import com.methodia.minibilling.controller.dto.report.AuditLogResponse;
import com.methodia.minibilling.controller.dto.report.BillingErrorLogResponse;
import com.methodia.minibilling.controller.dto.common.PageResponse;
import com.methodia.minibilling.service.audit.AuditService;
import com.methodia.minibilling.service.audit.BillingErrorLogService;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {

    private final AuditService auditService;
    private final BillingErrorLogService billingErrorLogService;

    public AuditController(AuditService auditService, BillingErrorLogService billingErrorLogService) {
        this.auditService = auditService;
        this.billingErrorLogService = billingErrorLogService;
    }

    @GetMapping("/api/logs/audit")
    public PageResponse<AuditLogResponse> logs(Pageable pageable) {
        return PageResponse.from(auditService.list(pageable));
    }

    @GetMapping("/api/logs/errors")
    public PageResponse<BillingErrorLogResponse> errors(Pageable pageable) {
        return PageResponse.from(billingErrorLogService.list(pageable));
    }
}
