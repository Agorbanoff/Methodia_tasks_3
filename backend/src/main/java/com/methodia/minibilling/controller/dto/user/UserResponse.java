package com.methodia.minibilling.controller.dto.user;

public record UserResponse(String id, String name, String reference, String username, String role, int priceListNumber) {
}
