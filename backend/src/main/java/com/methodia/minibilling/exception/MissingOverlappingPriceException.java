package com.methodia.minibilling.exception;

import com.methodia.minibilling.model.reading.MeasurementPeriod;

public class MissingOverlappingPriceException extends RuntimeException {

    public MissingOverlappingPriceException(MeasurementPeriod measurement) {
        super("No overlapping price found for product "
                + measurement.product()
                + " and price list "
                + measurement.priceList());
    }
}
