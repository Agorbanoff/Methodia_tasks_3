package com.methodia.minibilling.importer;

import com.methodia.minibilling.config.BillingProperties;
import com.methodia.minibilling.exception.CsvRowParseException;
import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.PriceRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.repository.UserRepository;
import com.methodia.minibilling.repository.FileImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CsvImportServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private FileImportRepository fileImportRepository;

    @TempDir
    private Path tempDir;

    private CsvImportService csvImportService;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        csvImportService = new CsvImportService(
                new BillingProperties(tempDir.toString(), tempDir.resolve("out").toString()),
                userRepository,
                readingRepository,
                priceRepository,
                fileImportRepository,
                Clock.fixed(Instant.parse("2024-04-01T10:15:30Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void importsUsersCsvAndUpdatesExistingUserByReference() throws Exception {
        Path usersCsv = write("users.csv", """
                First User,%s-1,1
                Existing User,%s-2,1
                """.formatted(suffix, suffix));
        csvImportService.importUsers(usersCsv);

        Path updatedUsersCsv = write("users-updated.csv", """
                Updated User,%s-2,2
                """.formatted(suffix));
        CsvImportSummary summary = csvImportService.importUsers(updatedUsersCsv);

        assertThat(summary.importedUsers()).isEqualTo(1);
        assertThat(userRepository.findByReference(suffix + "-1")).isPresent();
        assertThat(userRepository.findByReference(suffix + "-2"))
                .get()
                .satisfies(user -> {
                    assertThat(user.getName()).isEqualTo("Updated User");
                    assertThat(user.getPriceList()).isEqualTo(2);
                });
    }

    @Test
    void importsReadingsCsvForExistingUsers() throws Exception {
        UserEntity user = userRepository.save(new UserEntity("Reader User", suffix + "-reader", 1));
        Path readingsCsv = write("readings.csv", """
                %s-reader,gas,2024-01-01T12:00:00+02:00,1480.125
                %s-reader,elec,2024-02-01T12:00:00+02:00,1600
                """.formatted(suffix, suffix));

        CsvImportSummary summary = csvImportService.importReadings(readingsCsv);

        assertThat(summary.importedReadings()).isEqualTo(2);
        assertThat(summary.skippedDuplicates()).isZero();
        assertThat(readingRepository.findByUserOrderByDateTimeAsc(user)).hasSize(2);
        assertThat(readingRepository.findByUserAndProductOrderByDateTimeAsc(user, Product.GAS))
                .singleElement()
                .satisfies(reading -> {
                    assertThat(reading.getLastReading()).isEqualByComparingTo(new BigDecimal("1480.125"));
                    assertThat(reading.isInvoiced()).isFalse();
                    assertThat(reading.isSelfReported()).isFalse();
                    assertThat(reading.getFileImport().getFilename()).isEqualTo("readings.csv");
                });
    }

    @Test
    void importsPricesCsvForMultiplePriceLists() throws Exception {
        int priceListOne = uniquePriceList(1);
        int priceListTwo = uniquePriceList(2);
        Path pricesOneCsv = write("prices-1.csv", """
                gas,2024-01-01,2024-12-31,1.8000
                """);
        Path pricesTwoCsv = write("prices-2.csv", """
                gas,2024-01-01,2024-06-30,1.7000
                elec,2024-07-01,2024-12-31,0.3000
                """);

        CsvImportSummary firstSummary = csvImportService.importPrices(pricesOneCsv, priceListOne);
        CsvImportSummary secondSummary = csvImportService.importPrices(pricesTwoCsv, priceListTwo);

        assertThat(firstSummary.importedPrices()).isEqualTo(1);
        assertThat(secondSummary.importedPrices()).isEqualTo(2);
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(priceListOne, Product.GAS)).hasSize(1);
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(priceListTwo, Product.GAS)).hasSize(1);
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(priceListTwo, Product.ELECT)).hasSize(1);
    }

    @Test
    void repeatedImportDoesNotCreateReadingOrPriceDuplicates() throws Exception {
        UserEntity user = userRepository.save(new UserEntity("Duplicate User", suffix + "-dup", 1));
        int priceList = uniquePriceList(3);
        Path readingsCsv = write("readings.csv", """
                %s-dup,gas,2024-01-01T12:00:00+02:00,100
                """.formatted(suffix));
        Path pricesCsv = write("prices-3.csv", """
                gas,2024-01-01,2024-12-31,1.8000
                """);

        csvImportService.importReadings(readingsCsv);
        csvImportService.importPrices(pricesCsv, priceList);
        CsvImportSummary duplicateReadings = csvImportService.importReadings(readingsCsv);
        CsvImportSummary duplicatePrices = csvImportService.importPrices(pricesCsv, priceList);

        assertThat(duplicateReadings.skippedDuplicates()).isEqualTo(1);
        assertThat(duplicatePrices.skippedDuplicates()).isEqualTo(1);
        assertThat(readingRepository.findByUserAndProductOrderByDateTimeAsc(user, Product.GAS)).hasSize(1);
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(priceList, Product.GAS)).hasSize(1);
    }

    @Test
    void importAllDiscoversPricesFilesAndAggregatesSummary() throws Exception {
        write("users.csv", """
                Import All User,%s-all,1
                """.formatted(suffix));
        write("readings.csv", """
                %s-all,gas,2024-01-01T12:00:00+02:00,100
                """.formatted(suffix));
        write("prices-1.csv", """
                gas,2024-01-01,2024-06-30,1.8000
                """);
        write("prices-2.csv", """
                elec,2024-01-01,2024-06-30,0.3000
                """);

        CsvImportSummary summary = csvImportService.importAllFromInputDirectory();

        assertThat(summary.importedUsers()).isEqualTo(1);
        assertThat(summary.importedReadings()).isEqualTo(1);
        assertThat(summary.importedPrices()).isEqualTo(2);
        assertThat(summary.skippedDuplicates()).isZero();
        assertThat(summary.errors()).isEmpty();
    }

    @Test
    void invalidProductReturnsClearCsvError() throws Exception {
        userRepository.save(new UserEntity("Invalid Product User", suffix + "-invalid-product", 1));
        Path readingsCsv = write("readings.csv", """
                %s-invalid-product,water,2024-01-01T12:00:00+02:00,100
                """.formatted(suffix));

        assertThatThrownBy(() -> csvImportService.importReadings(readingsCsv))
                .isInstanceOf(CsvRowParseException.class)
                .hasMessageContaining("readings.csv")
                .hasMessageContaining("line 1")
                .hasMessageContaining("Invalid product");
    }

    @Test
    void missingUserReferenceInReadingReturnsClearCsvError() throws Exception {
        Path readingsCsv = write("readings.csv", """
                missing-reference,gas,2024-01-01T12:00:00+02:00,100
                """);

        assertThatThrownBy(() -> csvImportService.importReadings(readingsCsv))
                .isInstanceOf(CsvRowParseException.class)
                .hasMessageContaining("readings.csv")
                .hasMessageContaining("line 1")
                .hasMessageContaining("Unknown user reference: 'missing-reference'");
    }

    private Path write(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private int uniquePriceList(int offset) {
        return 100_000 + Integer.parseInt(suffix.substring(0, 4), 16) + offset;
    }
}
