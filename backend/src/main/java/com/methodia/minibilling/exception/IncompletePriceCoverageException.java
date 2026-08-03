package com.methodia.minibilling.exception;

import com.methodia.minibilling.model.reading.MeasurementPeriod;

public class IncompletePriceCoverageException extends RuntimeException {

    public IncompletePriceCoverageException(MeasurementPeriod measurement) {
        super("Price periods do not cover the full measurement period for product "
                + measurement.product()
                + " and price list "
                + measurement.priceList());
    }
}
