package com.methodia.minibilling.service;

import com.methodia.minibilling.exception.IncompletePriceCoverageException;
import com.methodia.minibilling.exception.MissingOverlappingPriceException;
import com.methodia.minibilling.model.MeasurementPeriod;
import com.methodia.minibilling.model.QuantityPricePeriod;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ProportionalDistributionService {

    private static final ZoneId SOFIA_ZONE = ZoneId.of("Europe/Sofia");
    private static final int SCALE = 2;

    public List<QuantityPricePeriod> distribute(MeasurementPeriod measurement, List<PriceEntity> prices) {
        List<MatchedPricePeriod> matchedPeriods = prices.stream()
                .filter(price -> price.getPriceList() == measurement.priceList())
                .filter(price -> price.getProduct() == measurement.product())
                .map(price -> toMatchedPeriod(measurement, price))
                .filter(MatchedPricePeriod::overlaps)
                .sorted(Comparator.comparing(MatchedPricePeriod::startDateTime))
                .toList();

        if (matchedPeriods.isEmpty()) {
            throw new MissingOverlappingPriceException(measurement);
        }
        if (!coversMeasurement(measurement, matchedPeriods)) {
            throw new IncompletePriceCoverageException(measurement);
        }

        long totalDays = inclusiveDays(measurement.startDateTime(), measurement.endDateTime());
        BigDecimal distributedBeforeLast = BigDecimal.ZERO.setScale(SCALE, RoundingMode.UP);
        List<QuantityPricePeriod> result = new ArrayList<>();

        for (int index = 0; index < matchedPeriods.size(); index++) {
            MatchedPricePeriod period = matchedPeriods.get(index);
            BigDecimal distributedQuantity;
            if (index == matchedPeriods.size() - 1) {
                distributedQuantity = measurement.quantity()
                        .subtract(distributedBeforeLast)
                        .setScale(SCALE, RoundingMode.UP);
            } else {
                BigDecimal ratio = BigDecimal.valueOf(inclusiveDays(period.startDateTime(), period.endDateTime()))
                        .divide(BigDecimal.valueOf(totalDays), SCALE, RoundingMode.UP);
                distributedQuantity = measurement.quantity()
                        .multiply(ratio)
                        .setScale(SCALE, RoundingMode.UP);
                distributedBeforeLast = distributedBeforeLast.add(distributedQuantity).setScale(SCALE, RoundingMode.UP);
            }

            result.add(new QuantityPricePeriod(
                    period.startDateTime(),
                    period.endDateTime(),
                    distributedQuantity,
                    measurement.product(),
                    period.price().getPrice().setScale(SCALE, RoundingMode.UP),
                    measurement.priceList()
            ));
        }

        return result;
    }

    private MatchedPricePeriod toMatchedPeriod(MeasurementPeriod measurement, PriceEntity price) {
        OffsetDateTime priceStart = price.getStartDate()
                .atStartOfDay(SOFIA_ZONE)
                .toOffsetDateTime();
        OffsetDateTime priceEnd = price.getEndDate()
                .atTime(23, 59, 59)
                .atZone(SOFIA_ZONE)
                .toOffsetDateTime();

        OffsetDateTime subStart = priceStart.isBefore(measurement.startDateTime())
                ? measurement.startDateTime()
                : priceStart;
        OffsetDateTime subEnd = priceEnd.isAfter(measurement.endDateTime())
                ? measurement.endDateTime()
                : priceEnd;

        return new MatchedPricePeriod(subStart, subEnd, price);
    }

    private boolean coversMeasurement(MeasurementPeriod measurement, List<MatchedPricePeriod> periods) {
        LocalDate expectedDate = measurement.startDateTime().atZoneSameInstant(SOFIA_ZONE).toLocalDate();
        LocalDate measurementEndDate = measurement.endDateTime().atZoneSameInstant(SOFIA_ZONE).toLocalDate();

        for (MatchedPricePeriod period : periods) {
            LocalDate periodStart = period.startDateTime().atZoneSameInstant(SOFIA_ZONE).toLocalDate();
            LocalDate periodEnd = period.endDateTime().atZoneSameInstant(SOFIA_ZONE).toLocalDate();
            if (periodStart.isAfter(expectedDate)) {
                return false;
            }
            if (!periodEnd.isBefore(expectedDate)) {
                expectedDate = periodEnd.plusDays(1);
            }
            if (expectedDate.isAfter(measurementEndDate)) {
                return true;
            }
        }

        return expectedDate.isAfter(measurementEndDate);
    }

    private long inclusiveDays(OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
        LocalDate startDate = startDateTime.atZoneSameInstant(SOFIA_ZONE).toLocalDate();
        LocalDate endDate = endDateTime.atZoneSameInstant(SOFIA_ZONE).toLocalDate();
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private record MatchedPricePeriod(
            OffsetDateTime startDateTime,
            OffsetDateTime endDateTime,
            PriceEntity price
    ) {

        boolean overlaps() {
            return !startDateTime.isAfter(endDateTime);
        }
    }
}
