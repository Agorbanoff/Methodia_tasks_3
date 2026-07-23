package com.methodia.minibilling.exception;

public class NoImportedDataException extends RuntimeException {

    public NoImportedDataException() {
        super("No imported data found. Please import CSV files first.");
    }
}
