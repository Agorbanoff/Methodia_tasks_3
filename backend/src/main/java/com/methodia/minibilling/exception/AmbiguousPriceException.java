package com.methodia.minibilling.exception;

import com.methodia.minibilling.model.Consumer;

public class AmbiguousPriceException extends BillingException {

    public AmbiguousPriceException(Consumer consumer, String product, int priceListNumber, int matches) {
        super(consumer.name(), consumer.reference(),
                "expected one applicable price for product '%s' and price list %d but found %d"
                        .formatted(product, priceListNumber, matches));
    }
}

