package com.methodia.minibilling.service;

import com.methodia.minibilling.exception.MissingPriceException;
import com.methodia.minibilling.model.Consumer;
import com.methodia.minibilling.model.Price;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class PricingService {

    public Price findApplicablePrice(Consumer consumer, String product, YearMonth invoiceMonth, List<Price> prices) {
        LocalDate periodStart = invoiceMonth.atDay(1);
        LocalDate periodEnd = invoiceMonth.atEndOfMonth();

        List<Price> matches = prices.stream()
                .filter(price -> price.priceListNumber() == consumer.priceListNumber())
                .filter(price -> price.product().equals(product))
                .filter(price -> !price.startDate().isAfter(periodEnd))
                .filter(price -> !price.endDate().isBefore(periodStart))
                .toList();

        if (matches.isEmpty()) {
            throw new MissingPriceException(consumer, product, consumer.priceListNumber());
        }
        return matches.getFirst();
    }
}
