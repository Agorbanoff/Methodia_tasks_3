package com.methodia.minibilling.controller;

import com.methodia.minibilling.config.CorsConfig;
import com.methodia.minibilling.config.SecurityConfig;
import com.methodia.minibilling.controller.dto.report.AuditLogResponse;
import com.methodia.minibilling.controller.dto.report.BillingErrorLogResponse;
import com.methodia.minibilling.controller.dto.auth.LoginRequest;
import com.methodia.minibilling.controller.dto.auth.LoginResponse;
import com.methodia.minibilling.model.ErrorSeverity;
import com.methodia.minibilling.model.ErrorStatus;
import com.methodia.minibilling.service.AuthenticationService;
import com.methodia.minibilling.service.AuditService;
import com.methodia.minibilling.service.BillingErrorLogService;
import com.methodia.minibilling.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, AuditController.class})
@Import({SecurityConfig.class, CorsConfig.class})
class AuthAndAuditSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private BillingErrorLogService billingErrorLogService;

    @Test
    void loginSetsHttpOnlyCookieAndReturnsFlatUserWithoutToken() throws Exception {
        when(jwtService.getExpirationSeconds()).thenReturn(86_400L);
        when(authenticationService.login(any(LoginRequest.class))).thenReturn(new AuthenticationService.LoginResult(
                "jwt-value",
                new LoginResponse("admin", "ADMIN", "admin")
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin"))))
                .andExpect(status().isOk())
                .andExpect(cookie().value(AuthController.AUTH_COOKIE_NAME, "jwt-value"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.reference").value("admin"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void protectedEndpointWorksWithAdminCookie() throws Exception {
        mockJwt("valid-admin", "admin", "ADMIN");
        when(auditService.list(any())).thenReturn(new PageImpl<>(
                List.of(new AuditLogResponse("id-1", OffsetDateTime.parse("2026-08-02T16:00:00+03:00"),
                        "admin", "LOGIN", "AUTH", "User logged in")),
                PageRequest.of(0, 20),
                1
        ));

        mockMvc.perform(get("/api/logs/audit").cookie(authCookie("valid-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].username").value("admin"))
                .andExpect(jsonPath("$.content[0].action").value("LOGIN"));
    }

    @Test
    void errorLogUsesFrontendContract() throws Exception {
        mockJwt("valid-admin", "admin", "ADMIN");
        when(billingErrorLogService.list(any())).thenReturn(new PageImpl<>(
                List.of(new BillingErrorLogResponse(
                        OffsetDateTime.parse("2026-08-02T18:00:00+03:00"),
                        "error-id",
                        "MISSING_TARIFF",
                        "DUMMY-1001",
                        "BILLING",
                        ErrorSeverity.ERROR,
                        "No valid tariff was found",
                        ErrorStatus.OPEN
                )),
                PageRequest.of(0, 20),
                1
        ));

        mockMvc.perform(get("/api/logs/errors").cookie(authCookie("valid-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].occurredAt").exists())
                .andExpect(jsonPath("$.content[0].errorId").value("error-id"))
                .andExpect(jsonPath("$.content[0].errorType").value("MISSING_TARIFF"))
                .andExpect(jsonPath("$.content[0].customerId").value("DUMMY-1001"))
                .andExpect(jsonPath("$.content[0].module").value("BILLING"))
                .andExpect(jsonPath("$.content[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.content[0].description").value("No valid tariff was found"))
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].type").doesNotExist());
    }

    @Test
    void errorLogAcceptsOccurredAtSortAndStillReturnsOccurredAt() throws Exception {
        mockJwt("valid-admin", "admin", "ADMIN");
        when(billingErrorLogService.list(any())).thenReturn(new PageImpl<>(
                List.of(new BillingErrorLogResponse(
                        OffsetDateTime.parse("2026-08-02T18:00:00+03:00"),
                        "error-id",
                        "MISSING_TARIFF",
                        "DUMMY-1001",
                        "BILLING",
                        ErrorSeverity.ERROR,
                        "No valid tariff was found",
                        ErrorStatus.OPEN
                )),
                PageRequest.of(0, 20),
                1
        ));

        mockMvc.perform(get("/api/logs/errors")
                        .param("sort", "occurredAt,desc")
                        .cookie(authCookie("valid-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].occurredAt").exists())
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist());
    }

    @Test
    void invalidCookieReturnsUnauthorized() throws Exception {
        when(jwtService.parse("bad-token")).thenThrow(new JwtException("expired"));

        mockMvc.perform(get("/api/logs/audit").cookie(authCookie("bad-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCookieOnProtectedEndpointReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/logs/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotAccessAuditLogs() throws Exception {
        mockJwt("valid-user", "user", "USER");

        mockMvc.perform(get("/api/logs/audit").cookie(authCookie("valid-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutDeletesCookie() throws Exception {
        mockJwt("valid-admin", "admin", "ADMIN");

        mockMvc.perform(post("/api/auth/logout").cookie(authCookie("valid-admin")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthController.AUTH_COOKIE_NAME, 0))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("jwt"))));

        verify(auditService).record("LOGOUT", "admin", "AUTH", "User logged out");
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
