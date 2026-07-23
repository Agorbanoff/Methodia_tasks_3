package com.methodia.minibilling.csv;

import com.methodia.minibilling.exception.CsvRowParseException;
import com.methodia.minibilling.model.Reading;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadingCsvReaderTest {

    private final ReadingCsvReader reader = new ReadingCsvReader();

    @TempDir
    private Path tempDir;

    @Test
    void readsReadingsSuccessfullyAndIgnoresBlankRows() throws IOException {
        Path file = write("readings.csv", """
                REF-1,ELECTRICITY,2024-01-01T11:00:00+03:00,100.25

                REF-1,ELECTRICITY,2024-02-01T11:00:00+03:00,150.75
                """);

        List<Reading> readings = reader.read(file);

        assertThat(readings).containsExactly(
                new Reading("REF-1", "ELECTRICITY", OffsetDateTime.parse("2024-01-01T11:00:00+03:00"), new BigDecimal("100.25")),
                new Reading("REF-1", "ELECTRICITY", OffsetDateTime.parse("2024-02-01T11:00:00+03:00"), new BigDecimal("150.75"))
        );
    }

    @Test
    void throwsMeaningfulExceptionForInvalidNumber() throws IOException {
        Path file = write("readings.csv", "REF-1,ELECTRICITY,2024-01-01T11:00:00+03:00,abc");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(CsvRowParseException.class)
                .hasMessageContaining("readings.csv")
                .hasMessageContaining("line 1")
                .hasMessageContaining("Invalid decimal number");
    }

    @Test
    void throwsMeaningfulExceptionForInvalidDateTime() throws IOException {
        Path file = write("readings.csv", "REF-1,ELECTRICITY,2024-01-01,100.25");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(CsvRowParseException.class)
                .hasMessageContaining("readings.csv")
                .hasMessageContaining("line 1")
                .hasMessageContaining("Invalid date-time");
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

