package com.methodia.minibilling.service.billing;

import com.methodia.minibilling.controller.dto.billing.HealthResponse;
import com.methodia.minibilling.exception.BillingException;
import com.methodia.minibilling.exception.IncompletePriceCoverageException;
import com.methodia.minibilling.exception.MissingOverlappingPriceException;
import com.methodia.minibilling.exception.NoImportedDataException;
import com.methodia.minibilling.mapper.InvoiceEntityMapper;
import com.methodia.minibilling.model.invoice.Invoice;
import com.methodia.minibilling.model.reading.MeasurementPeriod;
import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.model.tariff.QuantityPricePeriod;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import com.methodia.minibilling.persistence.entity.InvoiceLineEntity;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.repository.InvoiceLineRepository;
import com.methodia.minibilling.repository.InvoiceRepository;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.PriceRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.service.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class BillingService {

    private static final int AMOUNT_SCALE = 2;
    private static final BigDecimal VAT_PERCENTAGE = new BigDecimal("20");
    private static final int FIRST_DOCUMENT_NUMBER = 1000;
    private static final ZoneId SOFIA_ZONE = ZoneId.of("Europe/Sofia");
    private static final BigDecimal WARNING_PERCENT = new BigDecimal("50.00");

    private final CustomerRepository customerRepository;
    private final ReadingRepository readingRepository;
    private final PriceRepository priceRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ProportionalDistributionService proportionalDistributionService;
    private final InvoiceEntityMapper invoiceEntityMapper;
    private final AuditService auditService;
    private final Clock clock;

    public BillingService(
            CustomerRepository customerRepository,
            ReadingRepository readingRepository,
            PriceRepository priceRepository,
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            ProportionalDistributionService proportionalDistributionService,
            InvoiceEntityMapper invoiceEntityMapper,
            AuditService auditService,
            Clock clock
    ) {
        this.customerRepository = customerRepository;
        this.readingRepository = readingRepository;
        this.priceRepository = priceRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.proportionalDistributionService = proportionalDistributionService;
        this.invoiceEntityMapper = invoiceEntityMapper;
        this.auditService = auditService;
        this.clock = clock;
    }

    public HealthResponse getHealth() {
        return new HealthResponse("UP");
    }

    @Transactional
    public synchronized InvoiceGenerationResult generateInvoices(int year, int month) {
        ensureImportedDataExists();

        YearMonth invoiceMonth = YearMonth.of(year, month);
        List<CustomerEntity> customers = customerRepository.findAll().stream()
                .sorted(Comparator.comparing(CustomerEntity::getReference))
                .toList();

        List<Invoice> result = new ArrayList<>();
        int generatedCount = 0;
        int nextInvoiceNumber = nextInvoiceNumber();
        int skippedExistingCount = 0;
        List<String> warnings = new ArrayList<>();

        for (CustomerEntity customer : customers) {
            InvoiceEntity existingInvoice = invoiceRepository
                    .findByCustomerAndBillingYearAndBillingMonth(customer, year, month)
                    .orElse(null);
            if (existingInvoice != null) {
                result.add(invoiceEntityMapper.toModel(existingInvoice));
                skippedExistingCount++;
                continue;
            }

            InvoiceEntity invoice = generateInvoiceEntity(customer, invoiceMonth, String.valueOf(nextInvoiceNumber), null);
            if (invoice.getLines().isEmpty()) {
                continue;
            }

            InvoiceEntity savedInvoice = invoiceRepository.save(invoice);
            markInvoiceReadings(savedInvoice);
            result.add(invoiceEntityMapper.toModel(savedInvoice));
            warnings.addAll(findConsumptionWarnings(customer, invoiceMonth));
            auditService.record("INVOICE_GENERATED", null, "BILLING",
                    "Generated invoice " + savedInvoice.getNumber() + " for customer " + customer.getReference());
            generatedCount++;
            nextInvoiceNumber++;
        }

        return new InvoiceGenerationResult(result, generatedCount, skippedExistingCount, warnings);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized InvoiceGenerationResult generateInvoiceForCustomer(
            CustomerEntity customer,
            YearMonth invoiceMonth,
            List<PriceEntity> frozenPrices
    ) {
        ensureImportedDataExists();

        InvoiceEntity existingInvoice = invoiceRepository
                .findByCustomerAndBillingYearAndBillingMonth(customer, invoiceMonth.getYear(), invoiceMonth.getMonthValue())
                .orElse(null);
        if (existingInvoice != null) {
            return new InvoiceGenerationResult(List.of(invoiceEntityMapper.toModel(existingInvoice)), 0, 1);
        }

        InvoiceEntity invoice = generateInvoiceEntity(customer, invoiceMonth, String.valueOf(nextInvoiceNumber()), frozenPrices);
        if (invoice.getLines().isEmpty()) {
            return new InvoiceGenerationResult(List.of(), 0, 0);
        }

        InvoiceEntity savedInvoice = invoiceRepository.save(invoice);
        markInvoiceReadings(savedInvoice);
        List<String> warnings = findConsumptionWarnings(customer, invoiceMonth);
        auditService.record("INVOICE_GENERATED", null, "BILLING",
                "Generated invoice " + savedInvoice.getNumber() + " for customer " + customer.getReference());
        return new InvoiceGenerationResult(List.of(invoiceEntityMapper.toModel(savedInvoice)), 1, 0, warnings);
    }

    private void ensureImportedDataExists() {
        if (customerRepository.count() == 0 || readingRepository.count() == 0 || priceRepository.count() == 0) {
            throw new NoImportedDataException();
        }
    }

    private InvoiceEntity generateInvoiceEntity(CustomerEntity customer, YearMonth invoiceMonth, String documentNumber,
                                                List<PriceEntity> frozenPrices) {
        AtomicInteger lineIndex = new AtomicInteger(1);
        List<ReadingEntity> readings = readingRepository.findByCustomerOrderByDateTimeAsc(customer);
        if (readings.isEmpty()) {
            throw new BillingException(customer.getName(), customer.getReference(), "expected at least two readings for any product but found 0");
        }

        List<InvoiceLineEntity> consumptionLines = readings.stream()
                .collect(Collectors.groupingBy(ReadingEntity::getProduct))
                .entrySet()
                .stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .flatMap(entry -> createLines(customer, entry.getKey(), entry.getValue(), invoiceMonth, lineIndex, frozenPrices).stream())
                .toList();
        List<InvoiceLineEntity> lines = new ArrayList<>(consumptionLines);
        lines.addAll(createChargeLines(customer, consumptionLines, Product.STANDING_CHARGE, lineIndex, frozenPrices));
        lines.addAll(createChargeLines(customer, consumptionLines, Product.CCL, lineIndex, frozenPrices));

        BigDecimal totalAmount = totalAmount(lines);
        BigDecimal vatAmount = vatAmount(totalAmount);
        BigDecimal totalAmountWithVat = totalAmount.add(vatAmount).setScale(AMOUNT_SCALE, RoundingMode.UP);

        InvoiceEntity invoice = new InvoiceEntity(
                null,
                OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS),
                documentNumber,
                customer,
                totalAmount,
                totalAmountWithVat,
                false,
                invoiceMonth.getYear(),
                invoiceMonth.getMonthValue(),
                new ArrayList<>()
        );
        lines.forEach(invoice::addLine);
        return invoice;
    }

    private List<InvoiceLineEntity> createLines(
            CustomerEntity customer,
            Product product,
            List<ReadingEntity> readings,
            YearMonth invoiceMonth,
            AtomicInteger lineIndex,
            List<PriceEntity> frozenPrices
    ) {
        List<ReadingEntity> sortedReadings = readings.stream()
                .sorted(Comparator.comparing(reading -> reading.getDateTime().toInstant()))
                .toList();
        if (sortedReadings.size() < 2) {
            throw new BillingException(customer.getName(), customer.getReference(),
                    "expected at least two readings for product '%s' but found %d".formatted(product, sortedReadings.size()));
        }

        List<PriceEntity> prices = pricesForProduct(customer, product, frozenPrices);
        List<InvoiceLineEntity> lines = new ArrayList<>();
        for (int index = 1; index < sortedReadings.size(); index++) {
            ReadingEntity previousReading = sortedReadings.get(index - 1);
            ReadingEntity currentReading = sortedReadings.get(index);
            if (!isRelevantToInvoiceMonth(previousReading.getDateTime(), currentReading.getDateTime(), invoiceMonth)) {
                continue;
            }

            BigDecimal quantity = currentReading.getLastReading()
                    .subtract(previousReading.getLastReading())
                    .setScale(AMOUNT_SCALE, RoundingMode.UP);
            if (quantity.signum() < 0) {
                throw new BillingException(customer.getName(), customer.getReference(),
                        "negative consumption for product '%s'".formatted(product));
            }

            MeasurementPeriod measurement = new MeasurementPeriod(
                    previousReading.getDateTime(),
                    currentReading.getDateTime(),
                    quantity,
                    product,
                    customer.getPriceList()
            );

            proportionalDistributionService.distribute(measurement, prices).stream()
                    .map(period -> createLine(lineIndex.getAndIncrement(), period))
                    .forEach(lines::add);
        }

        return lines;
    }

    private List<PriceEntity> pricesForProduct(CustomerEntity customer, Product product, List<PriceEntity> frozenPrices) {
        if (frozenPrices == null) {
            return priceRepository.findByPriceListAndProductOrderByStartDateAsc(customer.getPriceList(), product);
        }

        List<PriceEntity> prices = new ArrayList<>();
        for (PriceEntity price : frozenPrices) {
            if (price.getProduct() == product) {
                prices.add(price);
            }
        }
        prices.sort(Comparator.comparing(PriceEntity::getStartDate));
        return prices;
    }

    private InvoiceLineEntity createLine(int index, QuantityPricePeriod period) {
        BigDecimal amount = period.quantity()
                .multiply(period.price())
                .setScale(AMOUNT_SCALE, RoundingMode.UP);

        return new InvoiceLineEntity(
                null,
                index,
                null,
                null,
                period.quantity(),
                period.startDateTime(),
                period.endDateTime(),
                period.product(),
                period.price(),
                period.priceList(),
                amount
        );
    }

    private BigDecimal totalAmount(List<InvoiceLineEntity> lines) {
        return lines.stream()
                .map(InvoiceLineEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(AMOUNT_SCALE, RoundingMode.UP);
    }

    private BigDecimal vatAmount(BigDecimal totalAmount) {
        return totalAmount.multiply(VAT_PERCENTAGE)
                .divide(BigDecimal.valueOf(100), AMOUNT_SCALE, RoundingMode.UP);
    }

    private List<InvoiceLineEntity> createChargeLines(
            CustomerEntity customer,
            List<InvoiceLineEntity> consumptionLines,
            Product chargeProduct,
            AtomicInteger lineIndex,
            List<PriceEntity> frozenPrices
    ) {
        List<PriceEntity> prices = pricesForProduct(customer, chargeProduct, frozenPrices);
        List<InvoiceLineEntity> result = new ArrayList<>();
        for (InvoiceLineEntity consumptionLine : consumptionLines) {
            List<ChargePeriod> chargePeriods = chargeProduct == Product.STANDING_CHARGE
                    ? standingChargePeriods(consumptionLine, prices)
                    : cclPeriods(consumptionLine, prices);
            for (ChargePeriod period : chargePeriods) {
                result.add(createChargeLine(lineIndex.getAndIncrement(), consumptionLine, chargeProduct,
                        period.start(), period.end(), period.quantity(), period.price()));
            }
        }
        return result;
    }

    private List<ChargePeriod> standingChargePeriods(InvoiceLineEntity consumptionLine, List<PriceEntity> prices) {
        return matchedChargePrices(consumptionLine, prices, Product.STANDING_CHARGE).stream()
                .map(matchedPrice -> new ChargePeriod(
                        matchedPrice.start(),
                        matchedPrice.end(),
                        BigDecimal.valueOf(inclusiveDays(matchedPrice.start(), matchedPrice.end()))
                                .setScale(AMOUNT_SCALE, RoundingMode.UP),
                        matchedPrice.price()))
                .toList();
    }

    private List<ChargePeriod> cclPeriods(InvoiceLineEntity consumptionLine, List<PriceEntity> prices) {
        MeasurementPeriod measurement = new MeasurementPeriod(
                consumptionLine.getStartDateTime(),
                consumptionLine.getEndDateTime(),
                consumptionLine.getQuantity(),
                Product.CCL,
                consumptionLine.getPriceList()
        );
        return proportionalDistributionService.distribute(measurement, prices).stream()
                .map(period -> new ChargePeriod(period.startDateTime(), period.endDateTime(), period.quantity(), period.price()))
                .toList();
    }

    private InvoiceLineEntity createChargeLine(int index, InvoiceLineEntity sourceLine, Product product,
                                               OffsetDateTime start, OffsetDateTime end,
                                               BigDecimal quantity, BigDecimal price) {
        BigDecimal amount = quantity.multiply(price).setScale(AMOUNT_SCALE, RoundingMode.UP);
        return new InvoiceLineEntity(
                null,
                index,
                null,
                sourceLine,
                quantity,
                start,
                end,
                product,
                price,
                sourceLine.getPriceList(),
                amount
        );
    }

    private List<MatchedChargePrice> matchedChargePrices(InvoiceLineEntity sourceLine, List<PriceEntity> prices, Product product) {
        MeasurementPeriod measurement = new MeasurementPeriod(
                sourceLine.getStartDateTime(),
                sourceLine.getEndDateTime(),
                BigDecimal.ONE,
                product,
                sourceLine.getPriceList()
        );
        List<MatchedChargePrice> matchedPrices = prices.stream()
                .filter(price -> price.getPriceList() == sourceLine.getPriceList())
                .filter(price -> price.getProduct() == product)
                .map(price -> matchedChargePrice(sourceLine, price))
                .filter(MatchedChargePrice::overlaps)
                .sorted(Comparator.comparing(MatchedChargePrice::start))
                .toList();
        if (matchedPrices.isEmpty()) {
            throw new MissingOverlappingPriceException(measurement);
        }
        if (!coversChargePeriod(sourceLine, matchedPrices)) {
            throw new IncompletePriceCoverageException(measurement);
        }
        return matchedPrices;
    }

    private MatchedChargePrice matchedChargePrice(InvoiceLineEntity sourceLine, PriceEntity price) {
        OffsetDateTime priceStart = price.getStartDate().atStartOfDay(SOFIA_ZONE).toOffsetDateTime();
        OffsetDateTime priceEnd = price.getEndDate().atTime(23, 59, 59).atZone(SOFIA_ZONE).toOffsetDateTime();
        OffsetDateTime start = priceStart.isBefore(sourceLine.getStartDateTime()) ? sourceLine.getStartDateTime() : priceStart;
        OffsetDateTime end = priceEnd.isAfter(sourceLine.getEndDateTime()) ? sourceLine.getEndDateTime() : priceEnd;
        return new MatchedChargePrice(start, end, price.getPrice().setScale(AMOUNT_SCALE, RoundingMode.UP));
    }

    private boolean coversChargePeriod(InvoiceLineEntity sourceLine, List<MatchedChargePrice> periods) {
        java.time.LocalDate expectedDate = sourceLine.getStartDateTime().atZoneSameInstant(SOFIA_ZONE).toLocalDate();
        java.time.LocalDate endDate = sourceLine.getEndDateTime().atZoneSameInstant(SOFIA_ZONE).toLocalDate();
        for (MatchedChargePrice period : periods) {
            java.time.LocalDate periodStart = period.start().atZoneSameInstant(SOFIA_ZONE).toLocalDate();
            java.time.LocalDate periodEnd = period.end().atZoneSameInstant(SOFIA_ZONE).toLocalDate();
            if (periodStart.isAfter(expectedDate)) {
                return false;
            }
            if (!periodEnd.isBefore(expectedDate)) {
                expectedDate = periodEnd.plusDays(1);
            }
            if (expectedDate.isAfter(endDate)) {
                return true;
            }
        }
        return expectedDate.isAfter(endDate);
    }

    private long inclusiveDays(OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
        java.time.LocalDate start = startDateTime.atZoneSameInstant(SOFIA_ZONE).toLocalDate();
        java.time.LocalDate end = endDateTime.atZoneSameInstant(SOFIA_ZONE).toLocalDate();
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    private boolean isRelevantToInvoiceMonth(OffsetDateTime startDateTime, OffsetDateTime endDateTime, YearMonth invoiceMonth) {
        OffsetDateTime monthStart = invoiceMonth.atDay(1).atStartOfDay(SOFIA_ZONE).toOffsetDateTime();
        OffsetDateTime monthEnd = invoiceMonth.atEndOfMonth().atTime(23, 59, 59).atZone(SOFIA_ZONE).toOffsetDateTime();
        return !endDateTime.isBefore(monthStart) && !startDateTime.isAfter(monthEnd);
    }

    private void markInvoiceReadings(InvoiceEntity invoice) {
        HashSet<ReadingEntity> usedReadings = new HashSet<>();
        List<ReadingEntity> customerReadings = readingRepository.findByCustomerOrderByDateTimeAsc(invoice.getCustomer());
        for (InvoiceLineEntity line : invoice.getLines()) {
            customerReadings.stream()
                    .filter(reading -> reading.getProduct() == line.getProduct())
                    .filter(reading -> !reading.getDateTime().isBefore(line.getStartDateTime()))
                    .filter(reading -> !reading.getDateTime().isAfter(line.getEndDateTime()))
                    .forEach(usedReadings::add);
        }
        usedReadings.forEach(reading -> reading.setInvoiced(true));
        readingRepository.saveAll(usedReadings);
    }

    private List<String> findConsumptionWarnings(CustomerEntity customer, YearMonth invoiceMonth) {
        List<ReadingEntity> readings = readingRepository.findByCustomerOrderByDateTimeAsc(customer);
        List<String> warnings = new ArrayList<>();
        for (Product product : Product.values()) {
            BigDecimal currentConsumption = calculateConsumption(readings, product, invoiceMonth);
            BigDecimal previousConsumption = calculateConsumption(readings, product, invoiceMonth.minusMonths(1));
            if (!hasUnusualConsumption(currentConsumption, previousConsumption)) {
                continue;
            }

            BigDecimal deviationPercent = currentConsumption.subtract(previousConsumption).abs()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(previousConsumption, 2, RoundingMode.UP);
            warnings.add("Unusual consumption for customer %s and product %s: current %s, previous %s, deviation %s%%"
                    .formatted(customer.getReference(), product, currentConsumption, previousConsumption, deviationPercent));
        }
        return warnings;
    }

    private BigDecimal calculateConsumption(List<ReadingEntity> readings, Product product, YearMonth invoiceMonth) {
        List<ReadingEntity> productReadings = new ArrayList<>();
        for (ReadingEntity reading : readings) {
            if (reading.getProduct() == product) {
                productReadings.add(reading);
            }
        }
        productReadings.sort(Comparator.comparing(reading -> reading.getDateTime().toInstant()));

        BigDecimal consumption = BigDecimal.ZERO;
        for (int index = 1; index < productReadings.size(); index++) {
            ReadingEntity previousReading = productReadings.get(index - 1);
            ReadingEntity currentReading = productReadings.get(index);
            if (!isRelevantToInvoiceMonth(previousReading.getDateTime(), currentReading.getDateTime(), invoiceMonth)) {
                continue;
            }

            BigDecimal quantity = currentReading.getLastReading().subtract(previousReading.getLastReading());
            if (quantity.signum() > 0) {
                consumption = consumption.add(quantity);
            }
        }
        return consumption.setScale(AMOUNT_SCALE, RoundingMode.UP);
    }

    private boolean hasUnusualConsumption(BigDecimal currentConsumption, BigDecimal previousConsumption) {
        // With a zero previous period there is no meaningful percentage baseline, so no warning is raised.
        if (previousConsumption.signum() == 0) {
            return false;
        }
        BigDecimal deviationPercent = currentConsumption.subtract(previousConsumption).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(previousConsumption, 2, RoundingMode.UP);
        return deviationPercent.compareTo(WARNING_PERCENT) > 0;
    }

    private int nextInvoiceNumber() {
        return invoiceRepository.findAllByOrderByNumberAsc().stream()
                .map(InvoiceEntity::getNumber)
                .mapToInt(this::parseInvoiceNumber)
                .max()
                .orElse(FIRST_DOCUMENT_NUMBER - 1) + 1;
    }

    private int parseInvoiceNumber(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException exception) {
            return FIRST_DOCUMENT_NUMBER - 1;
        }
    }

    private record MatchedChargePrice(
            OffsetDateTime start,
            OffsetDateTime end,
            BigDecimal price
    ) {

        boolean overlaps() {
            return !start.isAfter(end);
        }
    }

    private record ChargePeriod(
            OffsetDateTime start,
            OffsetDateTime end,
            BigDecimal quantity,
            BigDecimal price
    ) {
    }
}
