package com.methodia.minibilling.service;

import com.methodia.minibilling.dto.HealthResponse;
import com.methodia.minibilling.model.Consumer;
import com.methodia.minibilling.model.Invoice;
import com.methodia.minibilling.model.InvoiceLine;
import com.methodia.minibilling.model.Price;
import com.methodia.minibilling.model.Reading;
import com.methodia.minibilling.repository.ConsumerCsvReader;
import com.methodia.minibilling.repository.InvoiceFileRepository;
import com.methodia.minibilling.repository.PriceCsvReader;
import com.methodia.minibilling.repository.ReadingCsvReader;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BillingService {

    private static final int AMOUNT_SCALE = 2;

    private final InvoiceFileRepository invoiceFileRepository;
    private final ConsumerCsvReader consumerCsvReader;
    private final ReadingCsvReader readingCsvReader;
    private final PriceCsvReader priceCsvReader;
    private final ConsumptionService consumptionService;
    private final PricingService pricingService;
    private final InvoiceNumberService invoiceNumberService;
    private final InvoiceStorageService invoiceStorageService;
    private final Clock clock;

    public BillingService(
            InvoiceFileRepository invoiceFileRepository,
            ConsumerCsvReader consumerCsvReader,
            ReadingCsvReader readingCsvReader,
            PriceCsvReader priceCsvReader,
            ConsumptionService consumptionService,
            PricingService pricingService,
            InvoiceNumberService invoiceNumberService,
            InvoiceStorageService invoiceStorageService,
            Clock clock
    ) {
        this.invoiceFileRepository = invoiceFileRepository;
        this.consumerCsvReader = consumerCsvReader;
        this.readingCsvReader = readingCsvReader;
        this.priceCsvReader = priceCsvReader;
        this.consumptionService = consumptionService;
        this.pricingService = pricingService;
        this.invoiceNumberService = invoiceNumberService;
        this.invoiceStorageService = invoiceStorageService;
        this.clock = clock;
    }

    public HealthResponse getHealth() {
        return new HealthResponse(
                "UP",
                invoiceFileRepository.inputDirectory().toString(),
                invoiceFileRepository.outputDirectory().toString()
        );
    }

    public synchronized InvoiceGenerationResult generateInvoices(int year, int month) {
        YearMonth invoiceMonth = YearMonth.of(year, month);
        List<Consumer> consumers = consumerCsvReader.read(invoiceFileRepository.consumersFile());
        List<Reading> readings = readingCsvReader.read(invoiceFileRepository.readingsFile());
        List<Price> prices = priceCsvReader.readAll(invoiceFileRepository.inputDirectory());
        List<Invoice> result = new ArrayList<>();
        List<Consumer> newInvoiceConsumers = new ArrayList<>();

        for (Consumer consumer : consumers) {
            Optional<Invoice> existingInvoice = invoiceStorageService.findExistingInvoice(
                    invoiceFileRepository.outputDirectory(),
                    consumer.reference(),
                    invoiceMonth
            );
            if (existingInvoice.isPresent()) {
                result.add(existingInvoice.get());
            } else {
                newInvoiceConsumers.add(consumer);
            }
        }

        Iterator<String> documentNumbers = invoiceNumberService
                .reserveDocumentNumbers(invoiceFileRepository.outputDirectory(), newInvoiceConsumers.size())
                .iterator();

        List<Invoice> newInvoices = newInvoiceConsumers.stream()
                .map(consumer -> generateInvoice(consumer, readings, prices, invoiceMonth, documentNumbers.next()))
                .toList();

        invoiceStorageService.saveAll(invoiceFileRepository.outputDirectory(), newInvoices, invoiceMonth);
        result.addAll(newInvoices);
        return new InvoiceGenerationResult(result, newInvoices.size());
    }

    private Invoice generateInvoice(
            Consumer consumer,
            List<Reading> readings,
            List<Price> prices,
            YearMonth invoiceMonth,
            String documentNumber
    ) {
        AtomicInteger lineIndex = new AtomicInteger(1);
        Instant documentDate = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);

        List<InvoiceLine> lines = consumptionService.calculate(consumer, readings, invoiceMonth).stream()
                .map(consumption -> createLine(consumer, prices, invoiceMonth, lineIndex.getAndIncrement(), consumption))
                .toList();

        BigDecimal totalAmount = lines.stream()
                .map(InvoiceLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(AMOUNT_SCALE, RoundingMode.UP);

        return new Invoice(
                documentDate,
                documentNumber,
                consumer.name(),
                consumer.reference(),
                totalAmount,
                lines
        );
    }

    private InvoiceLine createLine(
            Consumer consumer,
            List<Price> prices,
            YearMonth invoiceMonth,
            int index,
            ConsumptionPeriod consumption
    ) {
        Price price = pricingService.findApplicablePrice(consumer, consumption.product(), invoiceMonth, prices);
        BigDecimal amount = consumption.quantity()
                .multiply(price.value())
                .setScale(AMOUNT_SCALE, RoundingMode.UP);

        return new InvoiceLine(
                index,
                consumption.quantity(),
                consumption.lineStart(),
                consumption.lineEnd(),
                consumption.product(),
                price.value(),
                price.priceListNumber(),
                amount
        );
    }

}
