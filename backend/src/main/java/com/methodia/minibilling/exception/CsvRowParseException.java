package com.methodia.minibilling.exception;

public class CsvRowParseException extends RuntimeException {

    private final String fileName;
    private final long lineNumber;
    private final String reason;

    public CsvRowParseException(String fileName, long lineNumber, String reason) {
        super("Invalid CSV row in %s at line %d: %s".formatted(fileName, lineNumber, reason));
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.reason = reason;
    }

    public String getFileName() {
        return fileName;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    public String getReason() {
        return reason;
    }
}

