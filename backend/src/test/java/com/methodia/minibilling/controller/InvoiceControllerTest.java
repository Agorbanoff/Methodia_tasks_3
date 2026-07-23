package com.methodia.minibilling.controller;

import com.methodia.minibilling.export.InvoiceDownload;
import com.methodia.minibilling.exception.GlobalExceptionHandler;
import com.methodia.minibilling.exception.InvoiceNotFoundException;
import com.methodia.minibilling.exception.NoImportedDataException;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.InvoiceLine;
import com.methodia.minibilling.service.BillingService;
import com.methodia.minibilling.service.InvoiceGenerationResult;
import com.methodia.minibilling.service.InvoiceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import(GlobalExceptionHandler.class)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingService billingService;

    @MockitoBean
    private InvoiceQueryService invoiceQueryService;

    @Test
    void generateReturnsCreatedResponseWithInvoiceSummaries() throws Exception {
        when(billingService.generateInvoices(2024, 3))
                .thenReturn(new InvoiceGenerationResult(List.of(invoice("1000", "Marko Boikov Tsvetkov", "1")), 1, 0));

        mockMvc.perform(post("/api/invoices/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "year": 2024,
                                  "month": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.month").value(3))
                .andExpect(jsonPath("$.generatedCount").value(1))
                .andExpect(jsonPath("$.skippedExistingCount").value(0))
                .andExpect(jsonPath("$.invoices[0].documentNumber").value("1000"))
                .andExpect(jsonPath("$.invoices[0].consumer").value("Marko Boikov Tsvetkov"))
                .andExpect(jsonPath("$.invoices[0].reference").value("1"))
                .andExpect(jsonPath("$.invoices[0].totalAmount").value(156.60))
                .andExpect(jsonPath("$.invoices[0].linesCount").value(1));
    }

    @Test
    void generateReturnsBadRequestWhenImportIsMissing() throws Exception {
        when(billingService.generateInvoices(2024, 3))
                .thenThrow(new NoImportedDataException());

        mockMvc.perform(post("/api/invoices/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "year": 2024,
                                  "month": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Import required"))
                .andExpect(jsonPath("$.detail").value("No imported data found. Please import CSV files first."));
    }

    @Test
    void generateReturnsBadRequestForInvalidBody() throws Exception {
        mockMvc.perform(post("/api/invoices/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "year": 2024,
                                  "month": 13
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findAllReturnsStoredInvoiceSummariesWithOptionalFilters() throws Exception {
        when(invoiceQueryService.findAll(eq(Optional.of(2024)), eq(Optional.of(3))))
                .thenReturn(List.of(invoice("1000", "Marko Boikov Tsvetkov", "1")));

        mockMvc.perform(get("/api/invoices")
                        .param("year", "2024")
                        .param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentNumber").value("1000"))
                .andExpect(jsonPath("$[0].totalAmount").value(156.60))
                .andExpect(jsonPath("$[0].linesCount").value(1));
    }

    @Test
    void findByDocumentNumberReturnsDetailDto() throws Exception {
        when(invoiceQueryService.findByDocumentNumber("1000"))
                .thenReturn(invoice("1000", "Marko Boikov Tsvetkov", "1"));

        mockMvc.perform(get("/api/invoices/1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value("1000"))
                .andExpect(jsonPath("$.lines[0].product").value("gas"))
                .andExpect(jsonPath("$.lines[0].amount").value(156.60));
    }

    @Test
    void findByDocumentNumberReturnsNotFound() throws Exception {
        when(invoiceQueryService.findByDocumentNumber("9999"))
                .thenThrow(new InvoiceNotFoundException("9999"));

        mockMvc.perform(get("/api/invoices/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Invoice not found"));
    }

    @Test
    void downloadReturnsJsonAttachment() throws Exception {
        when(invoiceQueryService.download("1000"))
                .thenReturn(new InvoiceDownload("1000.json", "{\"documentNumber\":\"1000\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/invoices/1000/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("1000.json")))
                .andExpect(content().json("{\"documentNumber\":\"1000\"}"));
    }

    private Invoice invoice(String documentNumber, String consumer, String reference) {
        return new Invoice(
                Instant.parse("2024-03-05T10:15:30Z"),
                documentNumber,
                consumer,
                reference,
                new BigDecimal("156.60"),
                List.of(new InvoiceLine(
                        1,
                        new BigDecimal("10.000"),
                        Instant.parse("2024-03-01T08:00:00Z"),
                        Instant.parse("2024-03-31T08:00:00Z"),
                        "gas",
                        new BigDecimal("15.66"),
                        1,
                        new BigDecimal("156.60")
                ))
        );
    }
}
