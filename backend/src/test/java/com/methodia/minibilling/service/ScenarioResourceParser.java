package com.methodia.minibilling.service;

import com.methodia.minibilling.model.MeasurementPeriod;
import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.persistence.entity.PriceEntity;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

final class ScenarioResourceParser {

    private static final int PRICE_LIST = 1;
    private static final Product PRODUCT = Product.ELECT;

    private ScenarioResourceParser() {
    }

    static ScenarioInput parseInput(String scenario) {
        List<String> lines = readScenarioFile(scenario, "in.txt");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("%s/in.txt is empty".formatted(scenario));
        }

        int expectedRows = Integer.parseInt(lines.getFirst().trim());
        List<String> dataRows = lines.stream()
                .skip(1)
                .filter(line -> !line.trim().isEmpty())
                .toList();
        if (dataRows.size() != expectedRows) {
            throw new IllegalArgumentException("%s/in.txt declares %d rows but contains %d".formatted(scenario, expectedRows, dataRows.size()));
        }

        List<MeasurementPeriod> measurements = new ArrayList<>();
        List<PriceEntity> prices = new ArrayList<>();

        for (int index = 0; index < dataRows.size(); index++) {
            String[] values = split(dataRows.get(index), scenario, "in.txt", index + 2);
            switch (values[0]) {
                case "Q" -> {
                    requireColumnCount(values, 4, scenario, "in.txt", index + 2);
                    measurements.add(new MeasurementPeriod(
                            OffsetDateTime.parse(values[1]),
                            OffsetDateTime.parse(values[2]),
                            decimal(values[3]),
                            PRODUCT,
                            PRICE_LIST
                    ));
                }
                case "P" -> {
                    requireColumnCount(values, 4, scenario, "in.txt", index + 2);
                    prices.add(new PriceEntity(
                            null,
                            PRODUCT,
                            LocalDate.parse(values[1]),
                            LocalDate.parse(values[2]),
                            decimal(values[3]),
                            PRICE_LIST,
                            null
                    ));
                }
                default -> throw new IllegalArgumentException("%s/in.txt line %d has unknown row type: %s".formatted(scenario, index + 2, values[0]));
            }
        }

        return new ScenarioInput(List.copyOf(measurements), List.copyOf(prices));
    }

    static List<ScenarioExpectedLine> parseExpected(String scenario) {
        List<String> lines = readScenarioFile(scenario, "out.txt");
        List<ScenarioExpectedLine> expectedLines = new ArrayList<>();

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] values = split(line, scenario, "out.txt", index + 1);
            requireColumnCount(values, 4, scenario, "out.txt", index + 1);
            expectedLines.add(new ScenarioExpectedLine(
                    OffsetDateTime.parse(values[0]),
                    OffsetDateTime.parse(values[1]),
                    decimal(values[2]),
                    decimal(values[3])
            ));
        }

        return List.copyOf(expectedLines);
    }

    private static List<String> readScenarioFile(String scenario, String fileName) {
        try {
            Path path = Path.of(ClassLoader.getSystemResource("%s/%s".formatted(scenario, fileName)).toURI());
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalStateException("Could not read %s/%s".formatted(scenario, fileName), exception);
        }
    }

    private static String[] split(String line, String scenario, String fileName, int lineNumber) {
        String[] values = line.split(",", -1);
        for (int index = 0; index < values.length; index++) {
            values[index] = values[index].trim();
            if (values[index].isEmpty()) {
                throw new IllegalArgumentException("%s/%s line %d has blank column %d".formatted(scenario, fileName, lineNumber, index + 1));
            }
        }
        return values;
    }

    private static void requireColumnCount(String[] values, int expectedColumns, String scenario, String fileName, int lineNumber) {
        if (values.length != expectedColumns) {
            throw new IllegalArgumentException("%s/%s line %d expected %d columns but found %d".formatted(
                    scenario,
                    fileName,
                    lineNumber,
                    expectedColumns,
                    values.length
            ));
        }
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.UP);
    }
}
