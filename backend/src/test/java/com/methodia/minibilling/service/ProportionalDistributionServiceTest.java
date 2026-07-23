package com.methodia.minibilling.service;

import com.methodia.minibilling.exception.IncompletePriceCoverageException;
import com.methodia.minibilling.exception.MissingOverlappingPriceException;
import com.methodia.minibilling.model.MeasurementPeriod;
import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.model.QuantityPricePeriod;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProportionalDistributionServiceTest {

    private final ProportionalDistributionService service = new ProportionalDistributionService();

    @Test
    void distributesMeasurementWithSinglePrice() {
        List<QuantityPricePeriod> result = service.distribute(
                measurement("2024-03-01T10:00:00+02:00", "2024-03-31T10:00:00+03:00", "31.00"),
                List.of(price("2024-03-01", "2024-03-31", "2.001"))
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().quantity()).isEqualByComparingTo("31.00");
        assertThat(result.getFirst().price()).isEqualByComparingTo("2.01");
    }

    @Test
    void distributesMeasurementWithThreePrices() {
        List<QuantityPricePeriod> result = service.distribute(
                measurement("2024-03-01T10:00:00+02:00", "2024-03-31T10:00:00+03:00", "31.00"),
                List.of(
                        price("2024-03-21", "2024-03-31", "3.00"),
                        price("2024-03-01", "2024-03-10", "1.00"),
                        price("2024-03-11", "2024-03-20", "2.00")
                )
        );

        assertThat(result).hasSize(3);
        assertThat(result).extracting(QuantityPricePeriod::quantity)
                .containsExactly(new BigDecimal("10.23"), new BigDecimal("10.23"), new BigDecimal("10.54"));
        assertThat(result).extracting(QuantityPricePeriod::price)
                .containsExactly(new BigDecimal("1.00"), new BigDecimal("2.00"), new BigDecimal("3.00"));
    }

    @Test
    void lastPeriodTakesRemainingQuantity() {
        List<QuantityPricePeriod> result = service.distribute(
                measurement("2024-01-01T00:00:00+02:00", "2024-01-03T23:00:00+02:00", "10.00"),
                List.of(
                        price("2024-01-01", "2024-01-01", "1.00"),
                        price("2024-01-02", "2024-01-03", "2.00")
                )
        );

        assertThat(result).extracting(QuantityPricePeriod::quantity)
                .containsExactly(new BigDecimal("3.40"), new BigDecimal("6.60"));
        assertThat(result.stream().map(QuantityPricePeriod::quantity).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void countsCalendarDaysInclusively() {
        List<QuantityPricePeriod> result = service.distribute(
                measurement("2024-05-10T15:30:00+03:00", "2024-05-12T08:30:00+03:00", "30.00"),
                List.of(
                        price("2024-05-01", "2024-05-10", "1.00"),
                        price("2024-05-11", "2024-05-31", "2.00")
                )
        );

        assertThat(result).extracting(QuantityPricePeriod::quantity)
                .containsExactly(new BigDecimal("10.20"), new BigDecimal("19.80"));
    }

    @Test
    void usesEuropeSofiaTimezoneForPriceBoundaries() {
        List<QuantityPricePeriod> result = service.distribute(
                measurement("2024-06-01T00:00:00+03:00", "2024-06-02T23:59:59+03:00", "2.00"),
                List.of(price("2024-06-01", "2024-06-02", "1.00"))
        );

        assertThat(result.getFirst().startDateTime()).isEqualTo(OffsetDateTime.parse("2024-06-01T00:00:00+03:00"));
        assertThat(result.getFirst().endDateTime()).isEqualTo(OffsetDateTime.parse("2024-06-02T23:59:59+03:00"));
    }

    @Test
    void handlesOffsetChangeFromSummerToWinterTime() {
        List<QuantityPricePeriod> result = service.distribute(
                measurement("2024-10-26T10:00:00+03:00", "2024-10-28T10:00:00+02:00", "30.00"),
                List.of(
                        price("2024-10-26", "2024-10-27", "1.00"),
                        price("2024-10-28", "2024-10-28", "2.00")
                )
        );

        assertThat(result).extracting(QuantityPricePeriod::quantity)
                .containsExactly(new BigDecimal("20.10"), new BigDecimal("9.90"));
        assertThat(result.getFirst().endDateTime()).isEqualTo(OffsetDateTime.parse("2024-10-27T23:59:59+02:00"));
        assertThat(result.getLast().startDateTime()).isEqualTo(OffsetDateTime.parse("2024-10-28T00:00:00+02:00"));
    }

    @Test
    void throwsWhenPriceIsMissing() {
        assertThatThrownBy(() -> service.distribute(
                measurement("2024-03-01T10:00:00+02:00", "2024-03-31T10:00:00+03:00", "31.00"),
                List.of(price("2024-04-01", "2024-04-30", "2.00"))
        )).isInstanceOf(MissingOverlappingPriceException.class);
    }

    @Test
    void throwsWhenPriceCoverageIsIncomplete() {
        assertThatThrownBy(() -> service.distribute(
                measurement("2024-03-01T10:00:00+02:00", "2024-03-31T10:00:00+03:00", "31.00"),
                List.of(
                        price("2024-03-01", "2024-03-10", "1.00"),
                        price("2024-03-12", "2024-03-31", "2.00")
                )
        )).isInstanceOf(IncompletePriceCoverageException.class);
    }

    private MeasurementPeriod measurement(String startDateTime, String endDateTime, String quantity) {
        return new MeasurementPeriod(
                OffsetDateTime.parse(startDateTime),
                OffsetDateTime.parse(endDateTime),
                new BigDecimal(quantity),
                Product.GAS,
                1
        );
    }

    private PriceEntity price(String startDate, String endDate, String value) {
        return new PriceEntity(Product.GAS, LocalDate.parse(startDate), LocalDate.parse(endDate), new BigDecimal(value), 1);
    }
}
