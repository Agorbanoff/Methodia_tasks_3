package com.methodia.minibilling.importer;

import com.methodia.minibilling.controller.dto.importing.FileImportResult;
import com.methodia.minibilling.controller.dto.importing.FileImportResponse;
import com.methodia.minibilling.controller.dto.importing.ImportValidationError;
import com.methodia.minibilling.model.ImportType;
import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.model.ReadingSource;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.FileImportEntity;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.FileImportRepository;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.PriceRepository;
import com.methodia.minibilling.repository.ReadingRepository;
import com.methodia.minibilling.repository.UserRepository;
import com.methodia.minibilling.service.AuditService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CsvImportService {

    private static final ZoneId SOFIA_ZONE = ZoneId.of("Europe/Sofia");
    private static final Pattern TARIFF_CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
    private static final Pattern TARIFF_NUMBER_PATTERN = Pattern.compile("\\D*(\\d+).*");

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ReadingRepository readingRepository;
    private final PriceRepository priceRepository;
    private final FileImportRepository fileImportRepository;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CsvImportService(
            UserRepository userRepository,
            CustomerRepository customerRepository,
            ReadingRepository readingRepository,
            PriceRepository priceRepository,
            FileImportRepository fileImportRepository,
            AuditService auditService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.readingRepository = readingRepository;
        this.priceRepository = priceRepository;
        this.fileImportRepository = fileImportRepository;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public FileImportResponse importFiles(List<FileImportUpload> uploads, String authenticatedUsername) {
        if (uploads.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required");
        }

        Map<ImportType, TypedUpload> byType = new HashMap<>();
        for (FileImportUpload upload : uploads) {
            ImportType type = parseType(upload);
            if (upload.file() == null || upload.type() == null || upload.type().isBlank()) {
                throw new IllegalArgumentException(
                        "Each uploaded file must include files[index].type and files[index].file");
            }
            if (byType.putIfAbsent(type, new TypedUpload(type, upload)) != null) {
                throw new IllegalArgumentException("Duplicate import type in request: " + type);
            }
        }

        List<FileImportResult> results = new ArrayList<>();
        for (ImportType type : List.of(ImportType.USERS, ImportType.PRICES, ImportType.READINGS)) {
            TypedUpload upload = byType.get(type);
            if (upload != null) {
                results.add(importOneFile(upload, authenticatedUsername));
            }
        }
        return new FileImportResponse(results);
    }

    private FileImportResult importOneFile(TypedUpload upload, String authenticatedUsername) {
        return transactionTemplate.execute(status -> {
            UserEntity administrator = userRepository.findByUsername(authenticatedUsername).orElse(null);
            ParsedFile parsedFile = parse(upload);
            FileImportResult result = switch (upload.type()) {
                case USERS -> importCustomers(upload, parsedFile, administrator);
                case PRICES -> importTariffs(upload, parsedFile, administrator);
                case READINGS -> importUsage(upload, parsedFile, administrator);
            };
            auditService.record(result.success() ? "FILE_IMPORT_SUCCEEDED" : "FILE_IMPORT_REJECTED",
                    authenticatedUsername,
                    "IMPORT",
                    "type=%s file=%s importedRecords=%d validationErrors=%d"
                            .formatted(result.type(), result.fileName(), result.importedRecords(), result.errors().size()));
            return result;
        });
    }

    private FileImportResult importCustomers(TypedUpload upload, ParsedFile parsedFile, UserEntity administrator) {
        List<ImportValidationError> errors = new ArrayList<>(parsedFile.errors());
        if (!isExpectedFileName(upload.fileName(), "customer_data")) {
            errors.add(new ImportValidationError(null, "fileName",
                    "USERS/CUSTOMERS imports must use customer_data.csv or customer_data.xlsx"));
        }

        Set<String> references = new HashSet<>();
        List<CustomerRow> rows = new ArrayList<>();
        for (InputRow row : parsedFile.rows()) {
            validateColumnCount(row, 3, errors);
            String reference = value(row, 0);
            String name = value(row, 1);
            String tariffCode = value(row, 2);
            required(row, "reference", reference, errors);
            required(row, "name", name, errors);
            required(row, "tariffCode", tariffCode, errors);
            if (!tariffCode.isBlank() && !TARIFF_CODE_PATTERN.matcher(tariffCode).matches()) {
                errors.add(new ImportValidationError(row.number(), "tariffCode",
                        "Invalid tariff code: " + tariffCode));
            }
            if (!reference.isBlank() && !references.add(reference)) {
                errors.add(new ImportValidationError(row.number(), "reference",
                        "Duplicate customer reference in file: " + reference));
            }
            rows.add(new CustomerRow(reference, name, tariffCode));
        }

        if (!errors.isEmpty()) {
            saveImport(upload, administrator, "REJECTED", 0, errors.size());
            return result(upload, false, 0, errors);
        }

        for (CustomerRow row : rows) {
            CustomerEntity customer = customerRepository.findByReference(row.reference())
                    .orElseGet(() -> new CustomerEntity(row.reference(), row.name(), row.tariffCode()));
            customer.setName(row.name());
            customer.setTariffCode(row.tariffCode());
            customerRepository.save(customer);
        }
        saveImport(upload, administrator, "SUCCESS", rows.size(), 0);
        return result(upload, true, rows.size(), List.of());
    }

    private FileImportResult importUsage(TypedUpload upload, ParsedFile parsedFile, UserEntity administrator) {
        List<ImportValidationError> errors = new ArrayList<>(parsedFile.errors());
        if (!isExpectedFileName(upload.fileName(), "usage_data")) {
            errors.add(new ImportValidationError(null, "fileName",
                    "READINGS/USAGE imports must use usage_data.csv or usage_data.xlsx"));
        }

        Set<String> keys = new HashSet<>();
        List<UsageRow> rows = new ArrayList<>();
        for (InputRow row : parsedFile.rows()) {
            validateColumnCount(row, 4, errors);
            String reference = value(row, 0);
            String productValue = value(row, 1);
            String dateTimeValue = value(row, 2);
            String readingValue = value(row, 3);
            required(row, "reference", reference, errors);
            required(row, "product", productValue, errors);
            required(row, "dateTime", dateTimeValue, errors);
            required(row, "meterReading", readingValue, errors);

            CustomerEntity customer = reference.isBlank()
                    ? null
                    : customerRepository.findByReference(reference).orElse(null);
            if (!reference.isBlank() && customer == null) {
                errors.add(new ImportValidationError(row.number(), "reference",
                        "Unknown customer reference: " + reference));
            }

            Product product = parseProduct(row, productValue, errors);
            OffsetDateTime dateTime = parseOffsetDateTime(row, dateTimeValue, errors);
            BigDecimal reading = parseDecimal(row, "meterReading", readingValue, errors);
            if (reading != null && reading.signum() < 0) {
                errors.add(new ImportValidationError(row.number(), "meterReading", "Meter reading must not be negative"));
            }

            if (customer != null && product != null && dateTime != null) {
                String key = reference + "|" + product + "|" + dateTime.toInstant();
                if (!keys.add(key)) {
                    errors.add(new ImportValidationError(row.number(), "dateTime",
                            "Duplicate reading for customer, product and timestamp"));
                }
                if (readingRepository.existsByCustomerAndProductAndDateTime(customer, product, dateTime)) {
                    errors.add(new ImportValidationError(row.number(), "dateTime",
                            "Reading already exists for customer, product and timestamp"));
                }
                rows.add(new UsageRow(customer, product, dateTime, reading));
            }
        }

        if (!errors.isEmpty()) {
            saveImport(upload, administrator, "REJECTED", 0, errors.size());
            return result(upload, false, 0, errors);
        }

        FileImportEntity fileImport = saveImport(upload, administrator, "SUCCESS", rows.size(), 0);
        for (UsageRow row : rows) {
            readingRepository.save(new ReadingEntity(null, row.customer(), row.product(), row.dateTime(),
                    row.meterReading(), false, false, ReadingSource.IMPORTED, fileImport));
        }
        return result(upload, true, rows.size(), List.of());
    }

    private FileImportResult importTariffs(TypedUpload upload, ParsedFile parsedFile, UserEntity administrator) {
        List<ImportValidationError> errors = new ArrayList<>(parsedFile.errors());
        if (!isExpectedFileName(upload.fileName(), "tariff_plans")) {
            errors.add(new ImportValidationError(null, "fileName",
                    "PRICES/TARIFFS imports must use tariff_plans.csv or tariff_plans.xlsx"));
        }

        Set<String> exactRows = new HashSet<>();
        Map<String, List<TariffRow>> rowsByCode = new HashMap<>();
        List<TariffRow> rows = new ArrayList<>();
        for (InputRow row : parsedFile.rows()) {
            validateColumnCount(row, 4, errors);
            String tariffCode = value(row, 0);
            String startValue = value(row, 1);
            String endValue = value(row, 2);
            String priceValue = value(row, 3);
            required(row, "tariffCode", tariffCode, errors);
            required(row, "validFrom", startValue, errors);
            required(row, "validTo", endValue, errors);
            required(row, "unitPrice", priceValue, errors);
            if (!tariffCode.isBlank() && !TARIFF_CODE_PATTERN.matcher(tariffCode).matches()) {
                errors.add(new ImportValidationError(row.number(), "tariffCode",
                        "Invalid tariff code: " + tariffCode));
            }

            LocalDate start = parseLocalDate(row, "validFrom", startValue, errors);
            LocalDate end = parseLocalDate(row, "validTo", endValue, errors);
            BigDecimal price = parseDecimal(row, "unitPrice", priceValue, errors);
            if (start != null && end != null && start.isAfter(end)) {
                errors.add(new ImportValidationError(row.number(), "validFrom",
                        "Valid from must not be after valid to"));
            }
            if (price != null && price.signum() <= 0) {
                errors.add(new ImportValidationError(row.number(), "unitPrice", "Unit price must be greater than zero"));
            }
            if (start != null && end != null && price != null) {
                String exactKey = tariffCode + "|" + start + "|" + end + "|" + price.stripTrailingZeros();
                if (!exactRows.add(exactKey)) {
                    errors.add(new ImportValidationError(row.number(), "tariffCode", "Duplicate tariff row"));
                }
                TariffRow tariffRow = new TariffRow(row.number(), tariffCode, start, end, price);
                rows.add(tariffRow);
                rowsByCode.computeIfAbsent(tariffCode, ignored -> new ArrayList<>()).add(tariffRow);
            }
        }

        validateTariffOverlaps(rowsByCode, errors);
        validateExistingTariffs(rows, errors);

        if (!errors.isEmpty()) {
            saveImport(upload, administrator, "REJECTED", 0, errors.size());
            return result(upload, false, 0, errors);
        }

        FileImportEntity fileImport = saveImport(upload, administrator, "SUCCESS", rows.size(), 0);
        for (TariffRow row : rows) {
            int priceList = legacyPriceList(row.tariffCode());
            for (Product product : List.of(Product.GAS, Product.ELECT)) {
                PriceEntity price = new PriceEntity(null, product, row.startDate(), row.endDate(),
                        row.unitPrice(), priceList, fileImport);
                price.setTariffCode(row.tariffCode());
                priceRepository.save(price);
            }
        }
        return result(upload, true, rows.size(), List.of());
    }

    private ParsedFile parse(TypedUpload upload) {
        List<ImportValidationError> errors = new ArrayList<>();
        if (upload.file().isEmpty()) {
            errors.add(new ImportValidationError(null, "file", "File is empty"));
            return new ParsedFile(List.of(), errors);
        }
        String lowerFileName = upload.fileName().toLowerCase(Locale.ROOT);
        if (lowerFileName.endsWith(".csv")) {
            return parseCsv(upload, errors);
        }
        if (lowerFileName.endsWith(".xlsx")) {
            return parseXlsx(upload, errors);
        }
        errors.add(new ImportValidationError(null, "fileName", "Only .csv and .xlsx imports are supported"));
        return new ParsedFile(List.of(), errors);
    }

    private ParsedFile parseCsv(TypedUpload upload, List<ImportValidationError> errors) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(upload.file().getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {
            List<InputRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                InputRow row = new InputRow(Math.toIntExact(record.getRecordNumber()), values(record));
                if (rows.isEmpty() && isHeader(upload.type(), row)) {
                    continue;
                }
                rows.add(row);
            }
            if (rows.isEmpty()) {
                errors.add(new ImportValidationError(null, "file", "File contains no data rows"));
            }
            return new ParsedFile(rows, errors);
        } catch (IOException exception) {
            errors.add(new ImportValidationError(null, "file", "Could not read uploaded file"));
            return new ParsedFile(List.of(), errors);
        }
    }

    private ParsedFile parseXlsx(TypedUpload upload, List<ImportValidationError> errors) {
        try (Workbook workbook = WorkbookFactory.create(upload.file().getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                errors.add(new ImportValidationError(null, "file", "Workbook contains no worksheets"));
                return new ParsedFile(List.of(), errors);
            }

            Sheet sheet = workbook.getSheetAt(0);
            List<InputRow> rows = new ArrayList<>();
            for (Row sheetRow : sheet) {
                List<String> values = values(sheetRow, upload.type());
                if (isEmpty(values)) {
                    continue;
                }
                InputRow row = new InputRow(sheetRow.getRowNum() + 1, values);
                if (rows.isEmpty() && isHeader(upload.type(), row)) {
                    continue;
                }
                rows.add(row);
            }
            if (rows.isEmpty()) {
                errors.add(new ImportValidationError(null, "file", "File contains no data rows"));
            }
            return new ParsedFile(rows, errors);
        } catch (Exception exception) {
            errors.add(new ImportValidationError(null, "file", "Could not read uploaded workbook"));
            return new ParsedFile(List.of(), errors);
        }
    }

    private void validateExistingTariffs(List<TariffRow> rows, List<ImportValidationError> errors) {
        for (TariffRow row : rows) {
            List<PriceEntity> existing = priceRepository.findByTariffCodeAndProductOrderByStartDateAsc(row.tariffCode(), Product.GAS);
            for (PriceEntity price : existing) {
                if (!price.getStartDate().isAfter(row.endDate()) && !price.getEndDate().isBefore(row.startDate())) {
                    errors.add(new ImportValidationError(row.rowNumber(), "validFrom",
                            "Overlapping tariff period for code: " + row.tariffCode()));
                    break;
                }
            }
        }
    }

    private void validateTariffOverlaps(Map<String, List<TariffRow>> rowsByCode, List<ImportValidationError> errors) {
        for (List<TariffRow> group : rowsByCode.values()) {
            List<TariffRow> sorted = group.stream()
                    .sorted(Comparator.comparing(TariffRow::startDate))
                    .toList();
            for (int index = 1; index < sorted.size(); index++) {
                TariffRow previous = sorted.get(index - 1);
                TariffRow current = sorted.get(index);
                if (!current.startDate().isAfter(previous.endDate())) {
                    errors.add(new ImportValidationError(current.rowNumber(), "validFrom",
                            "Overlapping tariff period for code: " + current.tariffCode()));
                }
            }
        }
    }

    private FileImportEntity saveImport(TypedUpload upload, UserEntity administrator, String status, int importedRecords, int errorCount) {
        FileImportEntity entity = new FileImportEntity(null, upload.type(), upload.fileName(), administrator, OffsetDateTime.now(clock), null);
        entity.setStatus(status);
        entity.setImportedRecords(importedRecords);
        entity.setErrorCount(errorCount);
        return fileImportRepository.save(entity);
    }

    private FileImportResult result(TypedUpload upload, boolean success, int importedRecords, List<ImportValidationError> errors) {
        return new FileImportResult(upload.type().name(), upload.fileName(), success, importedRecords, errors);
    }

    private boolean isExpectedFileName(String fileName, String expectedBaseName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.equals(expectedBaseName + ".csv") || lower.equals(expectedBaseName + ".xlsx");
    }

    private boolean isHeader(ImportType type, InputRow row) {
        List<String> values = row.values().stream().map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
        return switch (type) {
            case USERS -> values.equals(List.of("customer reference", "customer name", "tariff code"));
            case READINGS -> values.equals(List.of("customer reference", "product", "reading date-time", "meter reading"));
            case PRICES -> values.equals(List.of("tariff code", "valid from", "valid to", "unit price"));
        };
    }

    private void validateColumnCount(InputRow row, int expected, List<ImportValidationError> errors) {
        if (row.values().size() != expected) {
            errors.add(new ImportValidationError(row.number(), "columns",
                    "Expected %d columns but found %d".formatted(expected, row.values().size())));
        }
    }

    private String value(InputRow row, int index) {
        return index < row.values().size() ? row.values().get(index).trim() : "";
    }

    private void required(InputRow row, String field, String value, List<ImportValidationError> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new ImportValidationError(row.number(), field, field + " is required"));
        }
    }

    private Product parseProduct(InputRow row, String value, List<ImportValidationError> errors) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "gas" -> Product.GAS;
            case "elec" -> Product.ELECT;
            default -> {
                if (!value.isBlank()) {
                    errors.add(new ImportValidationError(row.number(), "product",
                            "Invalid product: '%s'. Expected gas or elec".formatted(value)));
                }
                yield null;
            }
        };
    }

    private OffsetDateTime parseOffsetDateTime(InputRow row, String value, List<ImportValidationError> errors) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException exception) {
            errors.add(new ImportValidationError(row.number(), "dateTime", "Invalid ISO-8601 date-time"));
            return null;
        }
    }

    private LocalDate parseLocalDate(InputRow row, String field, String value, List<ImportValidationError> errors) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            errors.add(new ImportValidationError(row.number(), field, "Invalid ISO date"));
            return null;
        }
    }

    private BigDecimal parseDecimal(InputRow row, String field, String value, List<ImportValidationError> errors) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            errors.add(new ImportValidationError(row.number(), field, field + " must be numeric"));
            return null;
        }
    }

    private int legacyPriceList(String tariffCode) {
        Matcher matcher = TARIFF_NUMBER_PATTERN.matcher(tariffCode);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        return Math.abs(tariffCode.hashCode());
    }

    private List<String> values(CSVRecord record) {
        List<String> values = new ArrayList<>();
        record.forEach(values::add);
        return values;
    }

    private List<String> values(Row row, ImportType type) {
        int lastCell = row.getLastCellNum();
        List<String> values = new ArrayList<>();
        for (int index = 0; index < lastCell; index++) {
            values.add(value(row.getCell(index), type, index));
        }
        return values;
    }

    private String value(Cell cell, ImportType type, int columnIndex) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                LocalDateTime value = cell.getLocalDateTimeCellValue();
                if (type == ImportType.READINGS && columnIndex == 2) {
                    return value.atZone(SOFIA_ZONE).toOffsetDateTime().toString();
                }
                return value.toLocalDate().toString();
            }
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return cell.toString().trim();
    }

    private boolean isEmpty(List<String> values) {
        for (String value : values) {
            if (!value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private ImportType parseType(FileImportUpload upload) {
        String value = upload.type();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing import type for file: " + upload.fileName());
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "CUSTOMERS", "CUSTOMER", "USERS" -> ImportType.USERS;
            case "USAGE", "READINGS" -> ImportType.READINGS;
            case "TARIFFS", "TARIFF", "PRICES" -> ImportType.PRICES;
            default -> throw new IllegalArgumentException("Unsupported import type: " + value);
        };
    }

    private record TypedUpload(ImportType type, FileImportUpload upload) {

        String fileName() {
            return upload.fileName();
        }

        org.springframework.web.multipart.MultipartFile file() {
            return upload.file();
        }
    }

    private record ParsedFile(List<InputRow> rows, List<ImportValidationError> errors) {
    }

    private record InputRow(int number, List<String> values) {
    }

    private record CustomerRow(String reference, String name, String tariffCode) {
    }

    private record UsageRow(CustomerEntity customer, Product product, OffsetDateTime dateTime, BigDecimal meterReading) {
    }

    private record TariffRow(int rowNumber, String tariffCode, LocalDate startDate, LocalDate endDate, BigDecimal unitPrice) {
    }
}
