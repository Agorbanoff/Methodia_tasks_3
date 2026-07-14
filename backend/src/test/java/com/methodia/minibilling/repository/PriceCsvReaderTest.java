package com.methodia.minibilling.repository;

import com.methodia.minibilling.exception.CsvRowParseException;
import com.methodia.minibilling.model.Price;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceCsvReaderTest {

    private final PriceCsvReader reader = new PriceCsvReader();

    @TempDir
    private Path tempDir;

    @Test
    void readsPricesSuccessfullyAndUsesPriceListNumberFromFileName() throws IOException {
        Path file = write("prices-2.csv", """
                ELECTRICITY,2024-01-01,2024-01-31,0.42

                WATER,2024-01-01,2024-01-31,1.50
                """);

        List<Price> prices = reader.read(file);

        assertThat(prices).containsExactly(
                new Price("ELECTRICITY", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-31"), new BigDecimal("0.42"), 2),
                new Price("WATER", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-31"), new BigDecimal("1.50"), 2)
        );
    }

    @Test
    void throwsMeaningfulExceptionForInvalidNumber() throws IOException {
        Path file = write("prices-2.csv", "ELECTRICITY,2024-01-01,2024-01-31,abc");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(CsvRowParseException.class)
                .hasMessageContaining("prices-2.csv")
                .hasMessageContaining("line 1")
                .hasMessageContaining("Invalid decimal number");
    }

    @Test
    void throwsMeaningfulExceptionForInvalidDate() throws IOException {
        Path file = write("prices-2.csv", "ELECTRICITY,01-01-2024,2024-01-31,0.42");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(CsvRowParseException.class)
                .hasMessageContaining("prices-2.csv")
                .hasMessageContaining("line 1")
                .hasMessageContaining("Invalid date");
    }

    @Test
    void discoversAllPriceFilesByRegexAndIgnoresOtherFiles() throws IOException {
        write("prices-2.csv", "ELECTRICITY,2024-01-01,2024-01-31,0.42");
        write("prices-10.csv", "WATER,2024-01-01,2024-01-31,1.50");
        write("prices-a.csv", "SHOULD,NOT,BE,READ");
        write("users.csv", "Alice,REF-1,2");

        List<Price> prices = reader.readAll(tempDir);

        assertThat(prices)
                .extracting(Price::priceListNumber)
                .containsExactly(10, 2);
        assertThat(prices)
                .extracting(Price::product)
                .containsExactly("WATER", "ELECTRICITY");
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

