package com.methodia.minibilling.exception;

public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(String documentNumber) {
        super("Invoice with documentNumber '%s' was not found".formatted(documentNumber));
    }
}

