package com.methodia.minibilling.controller;

import com.methodia.minibilling.config.CorsConfig;
import com.methodia.minibilling.config.SecurityConfig;
import com.methodia.minibilling.controller.dto.reading.ReadingResponse;
import com.methodia.minibilling.service.JwtService;
import com.methodia.minibilling.service.ReadingService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(ReadingController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class ReadingControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadingService readingService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void unauthenticatedSelfReportSubmitReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/billing/readings/self-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-08-02",
                                  "service": "elec",
                                  "amount": 452.7
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotSubmitSelfReport() throws Exception {
        mockJwt("admin-token", "admin", "ADMIN");

        mockMvc.perform(post("/api/billing/readings/self-reports")
                        .cookie(authCookie("admin-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-08-02",
                                  "service": "elec",
                                  "amount": 452.7
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotAcceptSelfReport() throws Exception {
        mockJwt("user-token", "alice", "USER");

        mockMvc.perform(post("/api/billing/readings/self-reports/report-1/accept")
                        .cookie(authCookie("user-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCanListOwnReadingsRoute() throws Exception {
        mockJwt("user-token", "alice", "USER");
        when(readingService.listReadings(any(), any(), any(), any(), any(), any(), eq("alice")))
                .thenReturn(new PageImpl<>(List.of(new ReadingResponse(
                        "reading-id",
                        "DUMMY-1001",
                        "Acme Gas Household",
                        OffsetDateTime.parse("2026-07-31T23:59:59+03:00"),
                        "gas",
                        new BigDecimal("1137.50"),
                        false,
                        true
                )), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/billing/readings").cookie(authCookie("user-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].dateTime").exists())
                .andExpect(jsonPath("$.content[0].product").value("gas"))
                .andExpect(jsonPath("$.content[0].lastReading").value(1137.50))
                .andExpect(jsonPath("$.content[0].selfReported").value(false))
                .andExpect(jsonPath("$.content[0].invoiced").value(true))
                .andExpect(jsonPath("$.content[0].service").doesNotExist())
                .andExpect(jsonPath("$.content[0].amount").doesNotExist())
                .andExpect(jsonPath("$.content[0].source").doesNotExist());
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
