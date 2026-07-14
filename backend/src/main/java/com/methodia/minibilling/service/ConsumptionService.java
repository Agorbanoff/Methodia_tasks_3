package com.methodia.minibilling.service;

import com.methodia.minibilling.exception.InsufficientReadingsException;
import com.methodia.minibilling.exception.NegativeConsumptionException;
import com.methodia.minibilling.model.Consumer;
import com.methodia.minibilling.model.Reading;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConsumptionService {

    private static final int QUANTITY_SCALE = 3;

    public List<ConsumptionPeriod> calculate(Consumer consumer, List<Reading> allReadings, YearMonth invoiceMonth) {
        Map<String, List<Reading>> readingsByProduct = allReadings.stream()
                .filter(reading -> reading.reference().equals(consumer.reference()))
                .filter(reading -> isUntilEndOfMonth(reading, invoiceMonth))
                .collect(Collectors.groupingBy(Reading::product));

        if (readingsByProduct.isEmpty()) {
            throw new InsufficientReadingsException(consumer, "any", 0);
        }

        return readingsByProduct.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> calculateProductConsumption(consumer, entry.getKey(), entry.getValue()))
                .toList();
    }

    private ConsumptionPeriod calculateProductConsumption(Consumer consumer, String product, List<Reading> readings) {
        List<Reading> sortedReadings = readings.stream()
                .sorted(Comparator.comparing(reading -> reading.dateTime().toInstant()))
                .toList();

        if (sortedReadings.size() < 2) {
            throw new InsufficientReadingsException(consumer, product, sortedReadings.size());
        }

        Reading first = sortedReadings.getFirst();
        Reading last = sortedReadings.getLast();
        BigDecimal quantity = last.value()
                .subtract(first.value())
                .setScale(QUANTITY_SCALE, RoundingMode.UP);

        if (quantity.signum() < 0) {
            throw new NegativeConsumptionException(consumer, product);
        }

        return new ConsumptionPeriod(
                product,
                quantity,
                first.dateTime().toInstant(),
                last.dateTime().toInstant()
        );
    }

    private boolean isUntilEndOfMonth(Reading reading, YearMonth invoiceMonth) {
        return !YearMonth.from(reading.dateTime().toLocalDate()).isAfter(invoiceMonth);
    }
}
