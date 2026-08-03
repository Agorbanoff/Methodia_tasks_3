package com.methodia.minibilling.controller;

import com.methodia.minibilling.controller.dto.user.RegisterUserRequest;
import com.methodia.minibilling.controller.dto.user.UserResponse;
import com.methodia.minibilling.service.user.UserAdministrationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserAdministrationService userAdministrationService;

    public AdminUserController(UserAdministrationService userAdministrationService) {
        this.userAdministrationService = userAdministrationService;
    }

    @PostMapping
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request, Authentication authentication) {
        return userAdministrationService.register(request, authentication.getName());
    }
}
