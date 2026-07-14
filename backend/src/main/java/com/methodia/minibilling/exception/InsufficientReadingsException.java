package com.methodia.minibilling.exception;

import com.methodia.minibilling.model.Consumer;

public class InsufficientReadingsException extends BillingException {

    public InsufficientReadingsException(Consumer consumer, String product, int count) {
        super(consumer.name(), consumer.reference(),
                "expected at least two readings for product '%s' but found %d".formatted(product, count));
    }
}

