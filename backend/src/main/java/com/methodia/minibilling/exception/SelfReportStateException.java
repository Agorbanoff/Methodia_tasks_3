package com.methodia.minibilling.exception;

public class SelfReportStateException extends RuntimeException {

    public SelfReportStateException(String message) {
        super(message);
    }
}
