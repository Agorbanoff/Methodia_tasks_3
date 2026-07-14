package com.methodia.minibilling.service;

import com.methodia.minibilling.exception.InsufficientReadingsException;
import com.methodia.minibilling.exception.NegativeConsumptionException;
import com.methodia.minibilling.model.Consumer;
import com.methodia.minibilling.model.Reading;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsumptionServiceTest {

    private final ConsumptionService service = new ConsumptionService();
    private final Consumer consumer = new Consumer("Alice", "REF-1", 1);

    @Test
    void calculatesCorrectQuantityWithScaleThree() {
        List<ConsumptionPeriod> periods = service.calculate(consumer, List.of(
                reading("REF-1", "gas", "2024-01-01T11:00:00+03:00", "10"),
                reading("REF-1", "gas", "2024-01-31T11:00:00+03:00", "15.1234")
        ), YearMonth.of(2024, 1));

        assertThat(periods).hasSize(1);
        assertThat(periods.getFirst().quantity()).isEqualByComparingTo("5.124");
        assertThat(periods.getFirst().quantity().scale()).isEqualTo(3);
    }

    @Test
    void throwsWhenOnlyOneReadingExists() {
        assertThatThrownBy(() -> service.calculate(consumer, List.of(
                reading("REF-1", "gas", "2024-01-01T11:00:00+03:00", "10")
        ), YearMonth.of(2024, 1)))
                .isInstanceOf(InsufficientReadingsException.class)
                .hasMessageContaining("Alice")
                .hasMessageContaining("gas");
    }

    @Test
    void throwsWhenConsumptionIsNegative() {
        assertThatThrownBy(() -> service.calculate(consumer, List.of(
                reading("REF-1", "gas", "2024-01-01T11:00:00+03:00", "10"),
                reading("REF-1", "gas", "2024-01-31T11:00:00+03:00", "9")
        ), YearMonth.of(2024, 1)))
                .isInstanceOf(NegativeConsumptionException.class)
                .hasMessageContaining("negative consumption");
    }

    private Reading reading(String reference, String product, String dateTime, String value) {
        return new Reading(reference, product, OffsetDateTime.parse(dateTime), new BigDecimal(value));
    }
}

