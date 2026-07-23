package com.methodia.minibilling.controller;

import com.methodia.minibilling.controller.dto.GenerateInvoicesRequest;
import com.methodia.minibilling.controller.dto.GenerateInvoicesResponse;
import com.methodia.minibilling.controller.dto.InvoiceDetailResponse;
import com.methodia.minibilling.export.InvoiceDownload;
import com.methodia.minibilling.mapper.InvoiceMapper;
import com.methodia.minibilling.controller.dto.InvoiceSummaryResponse;
import com.methodia.minibilling.service.BillingService;
import com.methodia.minibilling.service.InvoiceGenerationResult;
import com.methodia.minibilling.service.InvoiceQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Validated
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final BillingService billingService;
    private final InvoiceQueryService invoiceQueryService;

    public InvoiceController(BillingService billingService, InvoiceQueryService invoiceQueryService) {
        this.billingService = billingService;
        this.invoiceQueryService = invoiceQueryService;
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateInvoicesResponse> generate(@Valid @RequestBody GenerateInvoicesRequest request) {
        InvoiceGenerationResult result = billingService.generateInvoices(request.year(), request.month());
        List<InvoiceSummaryResponse> invoices = result.invoices().stream()
                .map(InvoiceMapper::toSummary)
                .toList();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new GenerateInvoicesResponse(
                        request.year(),
                        request.month(),
                        result.generatedCount(),
                        result.skippedExistingCount(),
                        invoices
                ));
    }

    @GetMapping
    public List<InvoiceSummaryResponse> findAll(
            @RequestParam Optional<@Min(1900) @Max(2100) Integer> year,
            @RequestParam Optional<@Min(1) @Max(12) Integer> month
    ) {
        return invoiceQueryService.findAll(year, month).stream()
                .map(InvoiceMapper::toSummary)
                .toList();
    }

    @GetMapping("/{documentNumber}")
    public ResponseEntity<InvoiceDetailResponse> findByDocumentNumber(@PathVariable String documentNumber) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(InvoiceMapper.toDetail(invoiceQueryService.findByDocumentNumber(documentNumber)));
    }

    @GetMapping("/{documentNumber}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String documentNumber) {
        InvoiceDownload download = invoiceQueryService.download(documentNumber);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity
                .ok()
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(new ByteArrayResource(download.content()));
    }
}
