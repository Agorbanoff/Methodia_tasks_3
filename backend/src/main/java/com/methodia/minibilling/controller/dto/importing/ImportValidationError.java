package com.methodia.minibilling.controller.dto.importing;

public record ImportValidationError(Integer row, String field, String message) {
}
