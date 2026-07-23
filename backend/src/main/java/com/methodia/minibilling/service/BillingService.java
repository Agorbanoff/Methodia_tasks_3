package com.methodia.minibilling.service;

import com.methodia.minibilling.controller.dto.HealthResponse;
import com.methodia.minibilling.config.BillingProperties;
import com.methodia.minibilling.exception.BillingException;
import com.methodia.minibilling.exception.NoImportedDataException;
import com.methodia.minibilling.mapper.InvoiceEntityMapper;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.MeasurementPeriod;
import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.model.QuantityPricePeriod;
import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import com.methodia.minibilling.persistence.entity.InvoiceLineEntity;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.InvoiceLineRepository;
import com.methodia.minibilling.repository.InvoiceRepository;
import com.methodia.minibilling.repository.PriceRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.repository.UserRepository;
import org.springframework.stereotype.Service;
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
    private static final int FIRST_DOCUMENT_NUMBER = 1000;
    private static final ZoneId SOFIA_ZONE = ZoneId.of("Europe/Sofia");

    private final BillingProperties billingProperties;
    private final UserRepository userRepository;
    private final ReadingRepository readingRepository;
    private final PriceRepository priceRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ProportionalDistributionService proportionalDistributionService;
    private final InvoiceEntityMapper invoiceEntityMapper;
    private final Clock clock;

    public BillingService(
            BillingProperties billingProperties,
            UserRepository userRepository,
            ReadingRepository readingRepository,
            PriceRepository priceRepository,
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            ProportionalDistributionService proportionalDistributionService,
            InvoiceEntityMapper invoiceEntityMapper,
            Clock clock
    ) {
        this.billingProperties = billingProperties;
        this.userRepository = userRepository;
        this.readingRepository = readingRepository;
        this.priceRepository = priceRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.proportionalDistributionService = proportionalDistributionService;
        this.invoiceEntityMapper = invoiceEntityMapper;
        this.clock = clock;
    }

    public HealthResponse getHealth() {
        return new HealthResponse(
                "UP",
                java.nio.file.Path.of(billingProperties.inputDirectory()).normalize().toString(),
                java.nio.file.Path.of(billingProperties.outputDirectory()).normalize().toString()
        );
    }

    @Transactional
    public synchronized InvoiceGenerationResult generateInvoices(int year, int month) {
        ensureImportedDataExists();

        YearMonth invoiceMonth = YearMonth.of(year, month);
        List<UserEntity> users = userRepository.findAll().stream()
                .sorted(Comparator.comparing(UserEntity::getReference))
                .toList();

        List<Invoice> result = new ArrayList<>();
        int generatedCount = 0;
        int nextInvoiceNumber = nextInvoiceNumber();
        int skippedExistingCount = 0;

        for (UserEntity user : users) {
            InvoiceEntity existingInvoice = invoiceRepository
                    .findByUserAndBillingYearAndBillingMonth(user, year, month)
                    .orElse(null);
            if (existingInvoice != null) {
                result.add(invoiceEntityMapper.toModel(existingInvoice));
                skippedExistingCount++;
                continue;
            }

            InvoiceEntity invoice = generateInvoiceEntity(user, invoiceMonth, String.valueOf(nextInvoiceNumber));
            if (invoice.getLines().isEmpty()) {
                continue;
            }

            InvoiceEntity savedInvoice = invoiceRepository.save(invoice);
            markInvoiceReadings(savedInvoice);
            result.add(invoiceEntityMapper.toModel(savedInvoice));
            generatedCount++;
            nextInvoiceNumber++;
        }

        return new InvoiceGenerationResult(result, generatedCount, skippedExistingCount);
    }

    private void ensureImportedDataExists() {
        if (userRepository.count() == 0 || readingRepository.count() == 0 || priceRepository.count() == 0) {
            throw new NoImportedDataException();
        }
    }

    private InvoiceEntity generateInvoiceEntity(UserEntity user, YearMonth invoiceMonth, String documentNumber) {
        AtomicInteger lineIndex = new AtomicInteger(1);
        List<ReadingEntity> readings = readingRepository.findByUserOrderByDateTimeAsc(user);
        if (readings.isEmpty()) {
            throw new BillingException(user.getName(), user.getReference(), "expected at least two readings for any product but found 0");
        }

        List<InvoiceLineEntity> lines = readings.stream()
                .collect(Collectors.groupingBy(ReadingEntity::getProduct))
                .entrySet()
                .stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .flatMap(entry -> createLines(user, entry.getKey(), entry.getValue(), invoiceMonth, lineIndex).stream())
                .toList();

        BigDecimal totalAmount = lines.stream()
                .map(InvoiceLineEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(AMOUNT_SCALE, RoundingMode.UP);

        InvoiceEntity invoice = new InvoiceEntity(
                OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS),
                documentNumber,
                user,
                totalAmount,
                invoiceMonth.getYear(),
                invoiceMonth.getMonthValue()
        );
        lines.forEach(invoice::addLine);
        return invoice;
    }

    private List<InvoiceLineEntity> createLines(
            UserEntity user,
            Product product,
            List<ReadingEntity> readings,
            YearMonth invoiceMonth,
            AtomicInteger lineIndex
    ) {
        List<ReadingEntity> sortedReadings = readings.stream()
                .sorted(Comparator.comparing(reading -> reading.getDateTime().toInstant()))
                .toList();
        if (sortedReadings.size() < 2) {
            throw new BillingException(user.getName(), user.getReference(),
                    "expected at least two readings for product '%s' but found %d".formatted(product, sortedReadings.size()));
        }

        List<PriceEntity> prices = priceRepository.findByPriceListAndProductOrderByStartDateAsc(user.getPriceList(), product);
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
                throw new BillingException(user.getName(), user.getReference(),
                        "negative consumption for product '%s'".formatted(product));
            }

            MeasurementPeriod measurement = new MeasurementPeriod(
                    previousReading.getDateTime(),
                    currentReading.getDateTime(),
                    quantity,
                    product,
                    user.getPriceList()
            );

            proportionalDistributionService.distribute(measurement, prices).stream()
                    .map(period -> createLine(lineIndex.getAndIncrement(), period))
                    .forEach(lines::add);
        }

        return lines;
    }

    private InvoiceLineEntity createLine(int index, QuantityPricePeriod period) {
        BigDecimal amount = period.quantity()
                .multiply(period.price())
                .setScale(AMOUNT_SCALE, RoundingMode.UP);

        return new InvoiceLineEntity(
                index,
                period.quantity(),
                period.startDateTime(),
                period.endDateTime(),
                period.product(),
                period.price(),
                period.priceList(),
                amount
        );
    }

    private boolean isRelevantToInvoiceMonth(OffsetDateTime startDateTime, OffsetDateTime endDateTime, YearMonth invoiceMonth) {
        OffsetDateTime monthStart = invoiceMonth.atDay(1).atStartOfDay(SOFIA_ZONE).toOffsetDateTime();
        OffsetDateTime monthEnd = invoiceMonth.atEndOfMonth().atTime(23, 59, 59).atZone(SOFIA_ZONE).toOffsetDateTime();
        return !endDateTime.isBefore(monthStart) && !startDateTime.isAfter(monthEnd);
    }

    private void markInvoiceReadings(InvoiceEntity invoice) {
        HashSet<ReadingEntity> usedReadings = new HashSet<>();
        List<ReadingEntity> userReadings = readingRepository.findByUserOrderByDateTimeAsc(invoice.getUser());
        for (InvoiceLineEntity line : invoice.getLines()) {
            userReadings.stream()
                    .filter(reading -> reading.getProduct() == line.getProduct())
                    .filter(reading -> !reading.getDateTime().isBefore(line.getStartDateTime()))
                    .filter(reading -> !reading.getDateTime().isAfter(line.getEndDateTime()))
                    .forEach(usedReadings::add);
        }
        usedReadings.forEach(reading -> reading.setInvoiced(true));
        readingRepository.saveAll(usedReadings);
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

}
