package com.methodia.minibilling.exception;

import com.methodia.minibilling.model.Consumer;

public class MissingPriceException extends BillingException {

    public MissingPriceException(Consumer consumer, String product, int priceListNumber) {
        super(consumer.name(), consumer.reference(),
                "missing applicable price for product '%s' and price list %d".formatted(product, priceListNumber));
    }
}

