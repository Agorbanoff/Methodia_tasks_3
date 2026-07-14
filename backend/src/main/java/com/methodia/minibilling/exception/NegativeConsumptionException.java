package com.methodia.minibilling.exception;

import com.methodia.minibilling.model.Consumer;

public class NegativeConsumptionException extends BillingException {

    public NegativeConsumptionException(Consumer consumer, String product) {
        super(consumer.name(), consumer.reference(),
                "negative consumption for product '%s'".formatted(product));
    }
}

