package com.methodia.minibilling.exception;

public class BillingException extends RuntimeException {

    private final String consumer;
    private final String reference;

    public BillingException(String consumer, String reference, String message) {
        super("Could not generate invoice for consumer '%s' with reference '%s': %s"
                .formatted(consumer, reference, message));
        this.consumer = consumer;
        this.reference = reference;
    }

    public String getConsumer() {
        return consumer;
    }

    public String getReference() {
        return reference;
    }
}

