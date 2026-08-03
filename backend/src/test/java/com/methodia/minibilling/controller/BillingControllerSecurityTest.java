package com.methodia.minibilling.controller;

import com.methodia.minibilling.config.CorsConfig;
import com.methodia.minibilling.config.SecurityConfig;
import com.methodia.minibilling.controller.dto.invoice.InvoiceDetailResponse;
import com.methodia.minibilling.controller.dto.invoice.InvoiceLineResponse;
import com.methodia.minibilling.service.billing.BillingRunService;
import com.methodia.minibilling.service.billing.BillingService;
import com.methodia.minibilling.service.invoice.InvoiceQueryService;
import com.methodia.minibilling.service.auth.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(BillingController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class BillingControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingService billingService;

    @MockitoBean
    private InvoiceQueryService invoiceQueryService;

    @MockitoBean
    private BillingRunService billingRunService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void userCannotStartBillingRun() throws Exception {
        mockJwt("user-token", "alice", "USER");

        mockMvc.perform(post("/api/billing/runs")
                        .cookie(authCookie("user-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-07-01",
                                  "endDate": "2026-07-31",
                                  "reference": "all"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotListBillingRuns() throws Exception {
        mockJwt("user-token", "alice", "USER");

        mockMvc.perform(get("/api/billing/runs")
                        .cookie(authCookie("user-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyBillingGenerateEndpointIsRemoved() throws Exception {
        mockJwt("admin-token", "admin", "ADMIN");

        mockMvc.perform(post("/api/billing/generate")
                        .cookie(authCookie("admin-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodStart": "26-07",
                                  "periodEnd": "26-07"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void invoiceListUsesFrontendContractAndReferenceFilter() throws Exception {
        mockJwt("admin-token", "admin", "ADMIN");
        InvoiceDetailResponse invoice = new InvoiceDetailResponse(
                "invoice-id",
                "1000",
                Instant.parse("2026-07-31T21:00:00Z"),
                "Acme Gas Household",
                "DUMMY-1001",
                "2026-07-01",
                "2026-07-31",
                new BigDecimal("144.38"),
                1,
                List.of(new InvoiceLineResponse(
                        1,
                        "gas",
                        new BigDecimal("137.50"),
                        new BigDecimal("1.05"),
                        new BigDecimal("144.38"),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-07-15T23:59:59Z"),
                        "T1"
                ))
        );
        when(invoiceQueryService.findVisibleInvoices(eq(Optional.of("DUMMY-1001")), org.mockito.ArgumentMatchers.any(), eq("admin")))
                .thenReturn(new PageImpl<>(List.of(invoice), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/billing/invoices")
                        .param("reference", "DUMMY-1001")
                        .cookie(authCookie("admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].linesCount").value(1))
                .andExpect(jsonPath("$.content[0].lines[0].product").value("gas"))
                .andExpect(jsonPath("$.content[0].lines[0].price").value(1.05))
                .andExpect(jsonPath("$.content[0].lines[0].lineStart").exists())
                .andExpect(jsonPath("$.content[0].lines[0].lineEnd").exists())
                .andExpect(jsonPath("$.content[0].lines[0].priceList").value("T1"))
                .andExpect(jsonPath("$.content[0].lines[0].service").doesNotExist())
                .andExpect(jsonPath("$.content[0].lines[0].unitPrice").doesNotExist());
    }

    private Cookie authCookie(String token) {
        return new Cookie(AuthController.AUTH_COOKIE_NAME, token);
    }

    private void mockJwt(String token, String username, String role) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(username);
        when(claims.get("role", String.class)).thenReturn(role);
        when(jwtService.parse(token)).thenReturn(claims);
    }
}
