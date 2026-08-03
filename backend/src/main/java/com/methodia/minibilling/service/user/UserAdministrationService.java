package com.methodia.minibilling.service.user;

import com.methodia.minibilling.controller.dto.user.RegisterUserRequest;
import com.methodia.minibilling.controller.dto.user.UserResponse;
import com.methodia.minibilling.model.auth.UserRole;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.UserRepository;
import com.methodia.minibilling.service.audit.AuditService;
import com.methodia.minibilling.service.auth.AuthenticationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    public UserAdministrationService(UserRepository userRepository, CustomerRepository customerRepository, PasswordEncoder passwordEncoder,
                                     AuthenticationService authenticationService, AuditService auditService) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse register(RegisterUserRequest request, String actorReference) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already exists");
        }
        UserEntity user = new UserEntity(null, request.name(), request.reference(), request.priceListNumber(), new ArrayList<>());
        if (request.role() == UserRole.USER) {
            CustomerEntity customer = customerRepository.findByReference(request.reference())
                    .orElseGet(() -> customerRepository.save(new CustomerEntity(
                            request.reference(), request.name(), request.priceListNumber())));
            if (userRepository.existsByCustomer(customer)) {
                throw new IllegalArgumentException("Customer already has a user account");
            }
            user.setCustomer(customer);
        } else if (userRepository.existsByReference(request.reference())) {
            throw new IllegalArgumentException("User reference already exists");
        }
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role().name());
        UserEntity saved = userRepository.save(user);
        auditService.record("REGISTER_USER", actorReference, "ADMIN", "Registered user " + saved.getReference());
        return authenticationService.toResponse(saved);
    }
}
