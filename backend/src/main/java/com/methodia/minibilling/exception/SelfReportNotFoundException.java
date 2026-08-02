package com.methodia.minibilling.exception;

public class SelfReportNotFoundException extends RuntimeException {

    public SelfReportNotFoundException(String id) {
        super("Self report not found: " + id);
    }
}
