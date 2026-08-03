package com.methodia.minibilling.importer;

import com.methodia.minibilling.model.importing.ImportType;
import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.FileImportRepository;
import com.methodia.minibilling.repository.PriceRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "app.audit.log-file=target/test-audit-import.log")
class CsvImportServiceTest {

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private FileImportRepository fileImportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                truncate table invoice_lines, invoices, readings, prices, file_imports,
                self_reports, billing_run_items, billing_runs, users, customers restart identity cascade
                """);
        UserEntity admin = new UserEntity(null, "Administrator", "admin", 0, new ArrayList<>());
        admin.setUsername("admin");
        admin.setPasswordHash("hash");
        admin.setRole("ADMIN");
        userRepository.save(admin);
    }

    @Test
    void importsValidCustomerCsvWithoutHeader() {
        var response = csvImportService.importFiles(List.of(upload(ImportType.USERS, "customer_data.csv",
                """
                DUMMY-1001,Acme Gas Household,T1
                DUMMY-1002,Beta Electric Shop,T2
                """)), "admin");

        assertThat(response.results().getFirst().success())
                .withFailMessage(() -> response.results().getFirst().errors().toString())
                .isTrue();
        assertThat(response.results().getFirst().importedRecords()).isEqualTo(2);
        assertThat(customerRepository.findByReference("DUMMY-1001")).hasValueSatisfying(customer -> {
            assertThat(customer.getName()).isEqualTo("Acme Gas Household");
            assertThat(customer.getPriceList()).isEqualTo(1);
        });
        assertThat(userRepository.findByReference("DUMMY-1001")).isEmpty();
    }

    @Test
    void importsValidCustomerXlsxWithHeader() {
        var response = csvImportService.importFiles(List.of(xlsxUpload(ImportType.USERS, "customer_data.xlsx",
                List.of("customer reference", "customer name", "tariff code"),
                List.of("DUMMY-1001", "Acme Gas Household", "T1"),
                List.of("DUMMY-1002", "Beta Electric Shop", "T2")
        )), "admin");

        assertThat(response.results().getFirst().success())
                .withFailMessage(() -> response.results().getFirst().errors().toString())
                .isTrue();
        assertThat(response.results().getFirst().importedRecords()).isEqualTo(2);
        assertThat(customerRepository.findByReference("DUMMY-1001")).hasValueSatisfying(customer -> {
            assertThat(customer.getName()).isEqualTo("Acme Gas Household");
            assertThat(customer.getPriceList()).isEqualTo(1);
        });
        assertThat(userRepository.findByReference("DUMMY-1001")).isEmpty();
    }

    @Test
    void customerImportRejectsDuplicateReferenceAndRollsBackWholeFile() {
        var response = csvImportService.importFiles(List.of(upload(ImportType.USERS, "customer_data.csv",
                """
                DUMMY-1001,Acme,T1
                DUMMY-1001,Duplicate,T1
                """)), "admin");

        assertThat(response.results().getFirst().success()).isFalse();
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Duplicate customer reference"));
        assertThat(customerRepository.findByReference("DUMMY-1001")).isEmpty();
    }

    @Test
    void customerImportRejectsMissingColumnAndEmptyReference() {
        var response = csvImportService.importFiles(List.of(upload(ImportType.USERS, "customer_data.csv",
                ",Acme\n")), "admin");

        assertThat(response.results().getFirst().success()).isFalse();
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.field()).isEqualTo("reference"));
    }

    @Test
    void importsValidTariffCsvAndCreatesProductPricesForStringTariffCode() {
        var response = csvImportService.importFiles(List.of(upload(ImportType.PRICES, "tariff_plans.csv",
                """
                T1,2026-07-01,2026-07-15,1.05
                T1,2026-07-16,2026-07-31,1.12
                """)), "admin");

        assertThat(response.results().getFirst().success()).isTrue();
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(1, Product.GAS)).hasSize(2);
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(1, Product.ELECT)).hasSize(2);
    }

    @Test
    void importsFinalTaskCsvShapesWithStandingChargeAndCcl() {
        var response = csvImportService.importFiles(List.of(
                upload(ImportType.USERS, "customer_data.csv",
                        """
                        customer_id,customer_name
                        1002,Maria Petrova Petrova
                        """),
                upload(ImportType.PRICES, "tariff_plans.csv",
                        """
                        product,start_date,end_date,price,price_unit
                        Gas,2025-01-01,2025-01-31,1.80,kWh
                        Gas,2025-02-01,2025-12-31,2.00,kWh
                        Standing Charge,2025-01-01,2025-01-31,1.60,day
                        Standing Charge,2025-02-01,2025-12-31,1.80,day
                        CCL,2025-01-01,2025-01-31,0.02,kWh
                        CCL,2025-02-01,2025-12-31,0.03,kWh
                        """),
                upload(ImportType.READINGS, "usage_data.csv",
                        """
                        customer_id,product,quantity,unit,start_date,end_date
                        1002,Gas,436,kWh,2025-01-01,2025-03-11
                        """)
        ), "admin");

        assertThat(response.results()).allSatisfy(result ->
                assertThat(result.success()).withFailMessage(result.errors().toString()).isTrue());
        CustomerEntity customer = customerRepository.findByReference("1002").orElseThrow();
        assertThat(customer.getPriceList()).isEqualTo(1);
        assertThat(readingRepository.findByCustomerAndProductOrderByDateTimeAsc(customer, Product.GAS)).hasSize(2);
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(1, Product.STANDING_CHARGE)).hasSize(2);
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(1, Product.CCL)).hasSize(2);
    }

    @Test
    void tariffImportRejectsInvalidStandingChargeAndCclUnits() {
        var response = csvImportService.importFiles(List.of(upload(ImportType.PRICES, "tariff_plans.csv",
                """
                product,start_date,end_date,price,price_unit
                Standing Charge,2025-01-01,2025-01-31,1.60,kWh
                CCL,2025-01-01,2025-01-31,0.02,day
                """)), "admin");

        assertThat(response.results().getFirst().success()).isFalse();
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Standing Charge requires day unit"));
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("CCL requires energy unit"));
    }

    @Test
    void importsValidTariffXlsx() {
        var response = csvImportService.importFiles(List.of(xlsxUpload(ImportType.PRICES, "tariff_plans.xlsx",
                List.of("tariff code", "valid from", "valid to", "unit price"),
                List.of("T1", "2026-07-01", "2026-07-15", "1.05"),
                List.of("T1", "2026-07-16", "2026-07-31", "1.12")
        )), "admin");

        assertThat(response.results().getFirst().success()).isTrue();
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(1, Product.GAS)).hasSize(2);
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(1, Product.ELECT)).hasSize(2);
    }

    @Test
    void tariffImportRejectsInvalidRowsAndRollsBackWholeFile() {
        var response = csvImportService.importFiles(List.of(upload(ImportType.PRICES, "tariff_plans.csv",
                """
                T1,2026-07-16,2026-07-01,0
                T1,not-a-date,2026-07-31,1.12
                """)), "admin");

        assertThat(response.results().getFirst().success()).isFalse();
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Valid from must not be after valid to"));
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Unit price must be greater than zero"));
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Invalid ISO date"));
        assertThat(priceRepository.findByPriceListAndProductOrderByStartDateAsc(1, Product.GAS)).isEmpty();
    }

    @Test
    void tariffImportRejectsOverlappingPeriodsAndDuplicateRows() {
        var response = csvImportService.importFiles(List.of(upload(ImportType.PRICES, "tariff_plans.csv",
                """
                T1,2026-07-01,2026-07-15,1.05
                T1,2026-07-01,2026-07-15,1.05
                T1,2026-07-10,2026-07-31,1.12
                """)), "admin");

        assertThat(response.results().getFirst().success()).isFalse();
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Duplicate tariff row"));
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Overlapping tariff period"));
    }

    @Test
    void importsValidUsageCsvWithElecAndOffsetDateTime() {
        CustomerEntity customer = saveCustomer("DUMMY-1001", "T1");

        var response = csvImportService.importFiles(List.of(upload(ImportType.READINGS, "usage_data.csv",
                """
                DUMMY-1001,elec,2026-07-01T00:00:00+03:00,1000.00
                DUMMY-1001,gas,2026-07-31T23:59:59+03:00,1137.50
                """)), "admin");

        assertThat(response.results().getFirst().success()).isTrue();
        assertThat(readingRepository.findByCustomerAndProductOrderByDateTimeAsc(customer, Product.ELECT))
                .singleElement()
                .satisfies(reading -> {
                    assertThat(reading.getDateTime()).isEqualTo(OffsetDateTime.parse("2026-07-01T00:00:00+03:00"));
                    assertThat(reading.getSource()).isEqualTo(com.methodia.minibilling.model.reading.ReadingSource.IMPORTED);
                });
    }

    @Test
    void importsValidUsageXlsxAndRollsBackMalformedWorkbook() {
        CustomerEntity customer = saveCustomer("DUMMY-1001", "T1");

        var valid = csvImportService.importFiles(List.of(xlsxUpload(ImportType.READINGS, "usage_data.xlsx",
                List.of("customer reference", "product", "reading date-time", "meter reading"),
                List.of("DUMMY-1001", "elec", "2026-07-01T00:00:00+03:00", "1000.00")
        )), "admin");

        assertThat(valid.results().getFirst().success())
                .withFailMessage(() -> valid.results().getFirst().errors().toString())
                .isTrue();
        assertThat(readingRepository.findByCustomerAndProductOrderByDateTimeAsc(customer, Product.ELECT))
                .singleElement()
                .satisfies(reading ->
                        assertThat(reading.getDateTime()).isEqualTo(OffsetDateTime.parse("2026-07-01T00:00:00+03:00")));

        var malformed = csvImportService.importFiles(List.of(new FileImportUpload("READINGS",
                new MockMultipartFile("file", "usage_data.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "not a workbook".getBytes(StandardCharsets.UTF_8)))), "admin");

        assertThat(malformed.results().getFirst().success()).isFalse();
        assertThat(malformed.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Could not read uploaded workbook"));
        assertThat(readingRepository.count()).isEqualTo(1);
    }

    @Test
    void usageImportRejectsUnknownInvalidNegativeDuplicateAndRollsBack() {
        saveCustomer("DUMMY-1001", "T1");

        var response = csvImportService.importFiles(List.of(upload(ImportType.READINGS, "usage_data.csv",
                """
                DUMMY-9999,gas,2026-07-01T00:00:00+03:00,1000.00
                DUMMY-1001,water,2026-07-01T00:00:00+03:00,1000.00
                DUMMY-1001,gas,2026-07-01T00:00:00+03:00,-1.00
                DUMMY-1001,gas,2026-07-01T00:00:00+03:00,1000.00
                """)), "admin");

        assertThat(response.results().getFirst().success()).isFalse();
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Unknown customer reference"));
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Invalid product"));
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("must not be negative"));
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Duplicate reading"));
        assertThat(readingRepository.count()).isZero();
    }

    @Test
    void invalidExtensionAndWrongLogicalFileNameReturnValidationResponse() {
        var response = csvImportService.importFiles(List.of(upload(ImportType.USERS, "usage_data.txt",
                "DUMMY-1001,Acme,T1\n")), "admin");

        assertThat(response.results().getFirst().success()).isFalse();
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("Only .csv and .xlsx imports are supported"));
        assertThat(response.results().getFirst().errors()).anySatisfy(error ->
                assertThat(error.message()).contains("customer_data.csv or customer_data.xlsx"));
    }

    @Test
    void multiFileRequestProcessesCustomerTariffUsageInLogicalOrder() {
        var response = csvImportService.importFiles(List.of(
                upload(ImportType.READINGS, "usage_data.csv", "DUMMY-1001,gas,2026-07-01T00:00:00+03:00,1000.00\n"),
                upload(ImportType.PRICES, "tariff_plans.csv", "T1,2026-07-01,2026-07-31,1.05\n"),
                upload(ImportType.USERS, "customer_data.csv", "DUMMY-1001,Acme,T1\n")
        ), "admin");

        assertThat(response.results()).extracting(result -> result.type())
                .containsExactly("USERS", "PRICES", "READINGS");
        assertThat(response.results()).allSatisfy(result -> assertThat(result.success()).isTrue());
        assertThat(customerRepository.findByReference("DUMMY-1001")).isPresent();
        assertThat(userRepository.findByReference("DUMMY-1001")).isEmpty();
        assertThat(readingRepository.count()).isEqualTo(1);
    }

    private CustomerEntity saveCustomer(String reference, String priceListValue) {
        return customerRepository.save(new CustomerEntity(reference, "Customer", priceList(priceListValue)));
    }

    private int priceList(String value) {
        return Integer.parseInt(value.replaceAll("\\D+", ""));
    }

    private FileImportUpload upload(ImportType type, String fileName, String content) {
        return new FileImportUpload(type.name(),
                new MockMultipartFile("file", fileName, "text/csv", content.getBytes(StandardCharsets.UTF_8)));
    }

    private FileImportUpload xlsxUpload(ImportType type, String fileName, List<String>... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("data");
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                var row = sheet.createRow(rowIndex);
                List<String> values = rows[rowIndex];
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    row.createCell(columnIndex).setCellValue(values.get(columnIndex));
                }
            }
            workbook.write(output);
            return new FileImportUpload(type.name(),
                    new MockMultipartFile("file", fileName,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            output.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create test workbook", exception);
        }
    }
}
