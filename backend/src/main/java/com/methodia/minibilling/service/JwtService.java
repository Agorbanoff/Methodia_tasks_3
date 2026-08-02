package com.methodia.minibilling.service;

import com.methodia.minibilling.persistence.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final Clock clock;
    private final long expirationSeconds;

    public JwtService(@Value("${app.security.jwt-secret:mini-billing-development-secret-key-change-me-please}") String secret,
                      @Value("${app.security.jwt-expiration-seconds:86400}") long expirationSeconds,
                      Clock clock) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.clock = clock;
    }

    public String createToken(UserEntity user) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("reference", user.customerReference())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
