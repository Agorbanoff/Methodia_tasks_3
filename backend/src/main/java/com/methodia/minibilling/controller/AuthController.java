package com.methodia.minibilling.controller;

import com.methodia.minibilling.controller.dto.auth.LoginRequest;
import com.methodia.minibilling.controller.dto.auth.LoginResponse;
import com.methodia.minibilling.service.auth.AuthenticationService;
import com.methodia.minibilling.service.audit.AuditService;
import com.methodia.minibilling.service.auth.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String AUTH_COOKIE_NAME = "mini_billing_auth";

    private final AuthenticationService authenticationService;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final boolean secureCookie;

    public AuthController(AuthenticationService authenticationService, JwtService jwtService, AuditService auditService,
                          @Value("${app.security.cookie-secure:false}") boolean secureCookie) {
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.LoginResult login = authenticationService.login(request);
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, login.token())
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(Duration.ofSeconds(jwtService.getExpirationSeconds()))
                .sameSite("Lax")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(login.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {
        if (authentication != null) {
            auditService.record("LOGOUT", authentication.getName(), "AUTH", "User logged out");
        }
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.noContent().build();
    }
}
