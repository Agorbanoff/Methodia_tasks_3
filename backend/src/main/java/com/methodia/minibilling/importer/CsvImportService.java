package com.methodia.minibilling.importer;

import com.methodia.minibilling.config.BillingProperties;
import com.methodia.minibilling.model.ImportType;
import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.persistence.entity.FileImportEntity;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.FileImportRepository;
import com.methodia.minibilling.repository.PriceRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.repository.UserRepository;
import com.methodia.minibilling.csv.CsvRowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class CsvImportService {

    private static final Pattern PRICE_FILE_PATTERN = Pattern.compile("prices-(\\d+)\\.csv");

    private final BillingProperties billingProperties;
    private final UserRepository userRepository;
    private final ReadingRepository readingRepository;
    private final PriceRepository priceRepository;
    private final FileImportRepository fileImportRepository;
    private final Clock clock;

    public CsvImportService(
            BillingProperties billingProperties,
            UserRepository userRepository,
            ReadingRepository readingRepository,
            PriceRepository priceRepository,
            FileImportRepository fileImportRepository,
            Clock clock
    ) {
        this.billingProperties = billingProperties;
        this.userRepository = userRepository;
        this.readingRepository = readingRepository;
        this.priceRepository = priceRepository;
        this.fileImportRepository = fileImportRepository;
        this.clock = clock;
    }

    @Transactional
    public CsvImportSummary importAllFromInputDirectory() {
        Path inputDirectory = Path.of(billingProperties.inputDirectory()).normalize();
        CsvImportSummary summary = importUsers(inputDirectory.resolve("users.csv"));
        summary = summary.add(importReadings(inputDirectory.resolve("readings.csv")));

        for (Path priceFile : discoverPriceFiles(inputDirectory)) {
            summary = summary.add(importPrices(priceFile, priceListNumber(priceFile)));
        }

        return summary;
    }

    @Transactional
    public CsvImportSummary importUsers(Path usersCsv) {
        ImportCounter counter = new ImportCounter();

        CsvRowMapper.readRows(usersCsv, 3, row -> {
            String reference = row.requiredString(1, "reference");
            UserEntity user = userRepository.findByReference(reference)
                    .orElseGet(() -> new UserEntity(row.requiredString(0, "name"), reference, row.requiredInt(2, "priceListNumber")));
            user.setName(row.requiredString(0, "name"));
            user.setPriceList(row.requiredInt(2, "priceListNumber"));
            userRepository.save(user);
            counter.imported++;
            return null;
        });

        return new CsvImportSummary(counter.imported, 0, 0, 0, List.of());
    }

    @Transactional
    public CsvImportSummary importReadings(Path readingsCsv) {
        FileImportEntity fileImport = saveFileImport(ImportType.READINGS, readingsCsv);
        ImportCounter counter = new ImportCounter();

        CsvRowMapper.readRows(readingsCsv, 4, row -> {
            String reference = row.requiredString(0, "reference");
            UserEntity user = userRepository.findByReference(reference)
                    .orElseThrow(() -> CsvRowMapper.invalid(readingsCsv, row.lineNumber(), "Unknown user reference: '%s'".formatted(reference)));
            Product product = parseProduct(readingsCsv, row.lineNumber(), row.requiredString(1, "product"));
            OffsetDateTime dateTime = row.requiredOffsetDateTime(2, "dateTime");

            if (readingRepository.existsByUserAndProductAndDateTime(user, product, dateTime)) {
                counter.skippedDuplicates++;
                return null;
            }

            ReadingEntity reading = new ReadingEntity(user, product, dateTime, row.requiredBigDecimal(3, "lastReading"));
            reading.setSelfReported(false);
            reading.setInvoiced(false);
            reading.setFileImport(fileImport);
            readingRepository.save(reading);
            counter.imported++;
            return null;
        });

        return new CsvImportSummary(0, counter.imported, 0, counter.skippedDuplicates, List.of());
    }

    @Transactional
    public CsvImportSummary importPrices(Path pricesCsv, int priceListNumber) {
        FileImportEntity fileImport = saveFileImport(ImportType.PRICES, pricesCsv);
        ImportCounter counter = new ImportCounter();

        CsvRowMapper.readRows(pricesCsv, 4, row -> {
            Product product = parseProduct(pricesCsv, row.lineNumber(), row.requiredString(0, "product"));
            java.time.LocalDate startDate = row.requiredLocalDate(1, "startDate");
            java.time.LocalDate endDate = row.requiredLocalDate(2, "endDate");

            if (priceRepository.existsByPriceListAndProductAndStartDateAndEndDate(priceListNumber, product, startDate, endDate)) {
                counter.skippedDuplicates++;
                return null;
            }

            PriceEntity price = new PriceEntity(product, startDate, endDate, row.requiredBigDecimal(3, "price"), priceListNumber);
            price.setFileImport(fileImport);
            priceRepository.save(price);
            counter.imported++;
            return null;
        });

        return new CsvImportSummary(0, 0, counter.imported, counter.skippedDuplicates, List.of());
    }

    private FileImportEntity saveFileImport(ImportType type, Path csvFile) {
        return fileImportRepository.save(new FileImportEntity(
                type,
                csvFile.getFileName().toString(),
                null,
                OffsetDateTime.now(clock),
                null
        ));
    }

    private List<Path> discoverPriceFiles(Path inputDirectory) {
        try (Stream<Path> files = Files.list(inputDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> PRICE_FILE_PATTERN.matcher(file.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not discover price CSV files in %s".formatted(inputDirectory), exception);
        }
    }

    private int priceListNumber(Path file) {
        String fileName = file.getFileName().toString();
        Matcher matcher = PRICE_FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            throw CsvRowMapper.invalid(file, 0, "File name must match prices-{number}.csv");
        }
        return Integer.parseInt(matcher.group(1));
    }

    private Product parseProduct(Path file, long lineNumber, String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "gas" -> Product.GAS;
            case "elec" -> Product.ELECT;
            default -> throw CsvRowMapper.invalid(file, lineNumber, "Invalid product: '%s'. Expected gas or elec".formatted(value));
        };
    }

    private static class ImportCounter {
        private int imported;
        private int skippedDuplicates;
    }
}
