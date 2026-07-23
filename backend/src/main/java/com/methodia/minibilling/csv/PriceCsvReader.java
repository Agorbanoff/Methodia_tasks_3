package com.methodia.minibilling.csv;

import com.methodia.minibilling.exception.CsvRowParseException;
import com.methodia.minibilling.model.Price;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class PriceCsvReader {

    private static final Pattern PRICE_FILE_PATTERN = Pattern.compile("prices-(\\d+)\\.csv");

    public List<Price> readAll(Path inputDirectory) {
        try (Stream<Path> files = Files.list(inputDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isPriceFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .flatMap(file -> read(file).stream())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not discover price CSV files in %s".formatted(inputDirectory), exception);
        }
    }

    public List<Price> read(Path file) {
        int priceListNumber = priceListNumber(file);
        return CsvRowMapper.readRows(file, 4, row -> new Price(
                row.requiredString(0, "product"),
                row.requiredLocalDate(1, "startDate"),
                row.requiredLocalDate(2, "endDate"),
                row.requiredBigDecimal(3, "value"),
                priceListNumber
        ));
    }

    private boolean isPriceFile(Path file) {
        return PRICE_FILE_PATTERN.matcher(file.getFileName().toString()).matches();
    }

    private int priceListNumber(Path file) {
        String fileName = file.getFileName().toString();
        Matcher matcher = PRICE_FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            throw new CsvRowParseException(fileName, 0, "File name must match prices-{number}.csv");
        }
        return Integer.parseInt(matcher.group(1));
    }
}

