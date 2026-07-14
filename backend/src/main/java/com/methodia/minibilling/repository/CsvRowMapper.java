package com.methodia.minibilling.repository;

import com.methodia.minibilling.exception.CsvRowParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class CsvRowMapper {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setIgnoreEmptyLines(false)
            .setTrim(false)
            .build();

    private CsvRowMapper() {
    }

    static <T> List<T> readRows(Path file, int expectedColumns, RowMapper<T> mapper) {
        List<String> lines = readLines(file);
        List<T> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            long lineNumber = i + 1L;
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }

            List<String> values = parseLine(file, lineNumber, line);
            if (values.size() != expectedColumns) {
                throw invalid(file, lineNumber, "Expected %d columns but found %d".formatted(expectedColumns, values.size()));
            }

            result.add(mapper.map(new Row(file, lineNumber, values)));
        }

        return result;
    }

    static CsvRowParseException invalid(Path file, long lineNumber, String reason) {
        return new CsvRowParseException(file.getFileName().toString(), lineNumber, reason);
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read CSV file %s".formatted(file), exception);
        }
    }

    private static List<String> parseLine(Path file, long lineNumber, String line) {
        try (CSVParser parser = CSVParser.parse(line, CSV_FORMAT)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.size() != 1) {
                throw invalid(file, lineNumber, "Expected one CSV record");
            }

            CSVRecord record = records.getFirst();
            List<String> values = new ArrayList<>();
            for (String value : record) {
                values.add(value.trim());
            }
            return values;
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof CsvRowParseException csvRowParseException) {
                throw csvRowParseException;
            }
            throw invalid(file, lineNumber, "Malformed CSV: %s".formatted(exception.getMessage()));
        }
    }

    @FunctionalInterface
    interface RowMapper<T> {
        T map(Row row);
    }

    record Row(Path file, long lineNumber, List<String> values) {

        String requiredString(int index, String fieldName) {
            String value = values.get(index);
            if (value.isBlank()) {
                throw invalid(file, lineNumber, "%s is required".formatted(fieldName));
            }
            return value;
        }

        int requiredInt(int index, String fieldName) {
            return parse(index, fieldName, Integer::parseInt, "Invalid integer");
        }

        BigDecimal requiredBigDecimal(int index, String fieldName) {
            return parse(index, fieldName, BigDecimal::new, "Invalid decimal number");
        }

        LocalDate requiredLocalDate(int index, String fieldName) {
            return parse(index, fieldName, LocalDate::parse, "Invalid date");
        }

        OffsetDateTime requiredOffsetDateTime(int index, String fieldName) {
            return parse(index, fieldName, OffsetDateTime::parse, "Invalid date-time");
        }

        private <T> T parse(int index, String fieldName, Function<String, T> parser, String errorPrefix) {
            String value = requiredString(index, fieldName);
            try {
                return parser.apply(value);
            } catch (NumberFormatException | DateTimeParseException exception) {
                throw invalid(file, lineNumber, "%s for %s: '%s'".formatted(errorPrefix, fieldName, value));
            }
        }
    }
}
