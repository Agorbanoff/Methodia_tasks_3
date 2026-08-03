package com.methodia.minibilling.service;

import com.methodia.minibilling.model.reading.MeasurementPeriod;
import com.methodia.minibilling.persistence.entity.PriceEntity;

import java.util.List;

record ScenarioInput(
        List<MeasurementPeriod> measurements,
        List<PriceEntity> prices
) {
}
