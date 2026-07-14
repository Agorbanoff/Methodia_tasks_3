package com.methodia.minibilling.service;

import com.methodia.minibilling.exception.MissingPriceException;
import com.methodia.minibilling.model.Consumer;
import com.methodia.minibilling.model.Price;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingServiceTest {

    private final PricingService service = new PricingService();

    @Test
    void selectsPriceByConsumerPriceListNumber() {
        Consumer consumer = new Consumer("Alice", "REF-1", 1);

        Price price = service.findApplicablePrice(consumer, "gas", YearMonth.of(2024, 1), List.of(
                price("gas", "2024-01-01", "2024-01-31", "1.00", 2),
                price("gas", "2024-01-01", "2024-01-31", "0.50", 1)
        ));

        assertThat(price.priceListNumber()).isEqualTo(1);
        assertThat(price.value()).isEqualByComparingTo("0.50");
    }

    @Test
    void throwsWhenPriceIsMissing() {
        Consumer consumer = new Consumer("Alice", "REF-1", 1);

        assertThatThrownBy(() -> service.findApplicablePrice(consumer, "gas", YearMonth.of(2024, 1), List.of(
                price("gas", "2024-01-01", "2024-01-31", "1.00", 2)
        )))
                .isInstanceOf(MissingPriceException.class)
                .hasMessageContaining("missing applicable price")
                .hasMessageContaining("Alice");
    }

    private Price price(String product, String startDate, String endDate, String value, int priceListNumber) {
        return new Price(product, LocalDate.parse(startDate), LocalDate.parse(endDate), new BigDecimal(value), priceListNumber);
    }
}

