package com.methodia.minibilling.controller;

import com.methodia.minibilling.controller.dto.billing.HealthResponse;
import com.methodia.minibilling.controller.dto.billing.BillingRunRequest;
import com.methodia.minibilling.controller.dto.billing.BillingRunResponse;
import com.methodia.minibilling.controller.dto.invoice.InvoiceDetailResponse;
import com.methodia.minibilling.controller.dto.common.PageResponse;
import com.methodia.minibilling.service.billing.BillingService;
import com.methodia.minibilling.service.billing.BillingRunService;
import com.methodia.minibilling.service.invoice.InvoiceQueryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;
    private final InvoiceQueryService invoiceQueryService;
    private final BillingRunService billingRunService;

    public BillingController(BillingService billingService, InvoiceQueryService invoiceQueryService,
                             BillingRunService billingRunService) {
        this.billingService = billingService;
        this.invoiceQueryService = invoiceQueryService;
        this.billingRunService = billingRunService;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity
                .ok()
                .body(billingService.getHealth());
    }

    @GetMapping("/invoices/{id}")
    public InvoiceDetailResponse findInvoice(@PathVariable String id, Authentication authentication) {
        return invoiceQueryService.findVisibleByDocumentNumber(id, authentication.getName());
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceDetailResponse> findInvoices(@RequestParam Optional<String> reference, Pageable pageable,
                                                            Authentication authentication) {
        return PageResponse.from(invoiceQueryService.findVisibleInvoices(reference, pageable, authentication.getName()));
    }

    @PostMapping("/runs")
    public BillingRunResponse startRun(@Valid @RequestBody BillingRunRequest request, Authentication authentication) {
        return billingRunService.start(request, authentication.getName());
    }

    @PostMapping("/runs/{runId}/stop")
    public BillingRunResponse stopRun(@PathVariable String runId, Authentication authentication) {
        return billingRunService.stop(runId, authentication.getName());
    }

    @PostMapping("/runs/{runId}/resume")
    public BillingRunResponse resumeRun(@PathVariable String runId, Authentication authentication) {
        return billingRunService.resume(runId, authentication.getName());
    }

    @PostMapping("/runs/{runId}/restart")
    public BillingRunResponse restartRun(@PathVariable String runId, Authentication authentication) {
        return billingRunService.restart(runId, authentication.getName());
    }

    @GetMapping("/runs/{runId}")
    public BillingRunResponse getRun(@PathVariable String runId) {
        return billingRunService.get(runId);
    }

    @GetMapping("/runs")
    public PageResponse<BillingRunResponse> listRuns(Pageable pageable) {
        return PageResponse.from(billingRunService.list(pageable));
    }

}
