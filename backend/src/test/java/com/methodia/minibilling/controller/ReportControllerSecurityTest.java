package com.methodia.minibilling.controller;

import com.methodia.minibilling.config.CorsConfig;
import com.methodia.minibilling.config.SecurityConfig;
import com.methodia.minibilling.controller.dto.report.BillingReportResponse;
import com.methodia.minibilling.controller.dto.billing.BillingRunResponse;
import com.methodia.minibilling.model.billing.BillingRunStatus;
import com.methodia.minibilling.service.billing.BillingRunService;
import com.methodia.minibilling.service.auth.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class ReportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingRunService billingRunService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void adminCanReadBillingReport() throws Exception {
        mockJwt("admin-token", "admin", "ADMIN");
        BillingRunResponse run = new BillingRunResponse("run-1", "2026-07-01", "2026-07-31",
                BillingRunStatus.COMPLETED, OffsetDateTime.parse("2026-07-01T10:00:00+03:00"),
                OffsetDateTime.parse("2026-07-01T10:00:05+03:00"), "admin", 2, 0, 1, 2, "RUN-run-1");
        when(billingRunService.report("run-1", "admin"))
                .thenReturn(new BillingReportResponse("run-1", BillingRunStatus.COMPLETED, 2, 1, 0, 1, 5,
                        "No failures", run));

        mockMvc.perform(get("/api/reports/billing-runs/run-1")
                        .cookie(authCookie("admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-1"))
                .andExpect(jsonPath("$.failureSummary").value("No failures"))
                .andExpect(jsonPath("$.warningRecords").value(1));
    }

    @Test
    void userCannotReadBillingReport() throws Exception {
        mockJwt("user-token", "alice", "USER");

        mockMvc.perform(get("/api/reports/billing-runs/run-1")
                        .cookie(authCookie("user-token")))
                .andExpect(status().isForbidden());
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
