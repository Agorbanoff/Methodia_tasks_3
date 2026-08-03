package com.methodia.minibilling.service.auth;

import com.methodia.minibilling.controller.dto.auth.LoginRequest;
import com.methodia.minibilling.controller.dto.auth.LoginResponse;
import com.methodia.minibilling.controller.dto.user.UserResponse;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.UserRepository;
import com.methodia.minibilling.service.audit.AuditService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                 JwtService jwtService, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!user.isEnabled() || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        auditService.record("LOGIN", user.getUsername(), "AUTH", "User logged in");
        return new LoginResult(jwtService.createToken(user),
                new LoginResponse(user.getUsername(), user.getRole(), user.customerReference()));
    }

    public UserResponse toResponse(UserEntity user) {
        return new UserResponse(user.getId(), user.displayName(), user.customerReference(), user.getUsername(),
                user.getRole(), user.effectivePriceList());
    }

    public record LoginResult(String token, LoginResponse response) {
    }
}
