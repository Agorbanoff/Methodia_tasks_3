package com.methodia.minibilling.controller.dto.user;

import com.methodia.minibilling.model.auth.UserRole;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterUserRequest(
        @NotBlank String name,
        @NotBlank String reference,
        @NotBlank String username,
        @NotBlank String password,
        @NotNull UserRole role,
        @Min(0) int priceListNumber
) {
}
