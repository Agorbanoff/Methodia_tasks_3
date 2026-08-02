package com.methodia.minibilling.config;

import com.methodia.minibilling.controller.AuthController;
import com.methodia.minibilling.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                // This API remains stateless: the HttpOnly cookie carries a JWT, not a server-side session token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            var authentication = SecurityContextHolder.getContext().getAuthentication();
                            if (authentication != null && authentication.isAuthenticated()
                                    && !"anonymousUser".equals(authentication.getName())) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                return;
                            }
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        })
                        .accessDeniedHandler((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/billing/health").permitAll()
                        .requestMatchers("/api/file/import").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/billing/readings/self-reports/*/accept",
                                "/api/billing/readings/self-reports/*/deny").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/billing/readings/self-reports").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/billing/readings",
                                "/api/billing/readings/self-reports").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/admin/**", "/api/billing/runs", "/api/billing/runs/**",
                                "/api/reports/**", "/api/logs/**").hasRole("ADMIN")
                        .requestMatchers("/api/billing/invoices/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Authentication uses JWT cookies");
        };
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final JwtService jwtService;
        private static final Set<String> SUPPORTED_ROLES = Set.of("ADMIN", "USER");

        JwtAuthenticationFilter(JwtService jwtService) {
            this.jwtService = jwtService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            Optional<String> token = resolveToken(request);
            if (token.isPresent()) {
                try {
                    Claims claims = jwtService.parse(token.get());
                    String role = claims.get("role", String.class);
                    if (!SUPPORTED_ROLES.contains(role)) {
                        throw new JwtException("Unsupported role");
                    }
                    var authentication = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    if (isFileImportRequest(request) && !"ADMIN".equals(role)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }
                } catch (JwtException | IllegalArgumentException exception) {
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            }
            filterChain.doFilter(request, response);
        }

        private Optional<String> resolveToken(HttpServletRequest request) {
            if (request.getCookies() != null) {
                Optional<String> cookieToken = Arrays.stream(request.getCookies())
                        .filter(cookie -> AuthController.AUTH_COOKIE_NAME.equals(cookie.getName()))
                        .map(Cookie::getValue)
                        .filter(value -> !value.isBlank())
                        .findFirst();
                if (cookieToken.isPresent()) {
                    return cookieToken;
                }
            }

            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                return Optional.of(header.substring(7));
            }
            return Optional.empty();
        }

        private boolean isFileImportRequest(HttpServletRequest request) {
            return "/api/file/import".equals(request.getServletPath())
                    || request.getRequestURI().endsWith("/api/file/import");
        }
    }
}
