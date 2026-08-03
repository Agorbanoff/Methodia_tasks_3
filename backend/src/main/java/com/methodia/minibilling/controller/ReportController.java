package com.methodia.minibilling.controller;

import com.methodia.minibilling.controller.dto.report.BillingReportResponse;
import com.methodia.minibilling.service.billing.BillingRunService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final BillingRunService billingRunService;

    public ReportController(BillingRunService billingRunService) {
        this.billingRunService = billingRunService;
    }

    @GetMapping("/billing-runs/{runId}")
    public BillingReportResponse billingRunReport(@PathVariable String runId, Authentication authentication) {
        return billingRunService.report(runId, authentication.getName());
    }
}
