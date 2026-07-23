package com.methodia.minibilling.csv;

import com.methodia.minibilling.exception.CsvRowParseException;
import com.methodia.minibilling.model.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsumerCsvReaderTest {

    private final ConsumerCsvReader reader = new ConsumerCsvReader();

    @TempDir
    private Path tempDir;

    @Test
    void readsConsumersSuccessfullyAndIgnoresBlankRows() throws IOException {
        Path file = write("users.csv", """
                Alice Adams, REF-1, 2

                   \s
                Bob Brown,REF-2,3
                """);

        List<Consumer> consumers = reader.read(file);

        assertThat(consumers).containsExactly(
                new Consumer("Alice Adams", "REF-1", 2),
                new Consumer("Bob Brown", "REF-2", 3)
        );
    }

    @Test
    void throwsMeaningfulExceptionForInvalidNumber() throws IOException {
        Path file = write("users.csv", "Alice Adams,REF-1,not-a-number");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(CsvRowParseException.class)
                .hasMessageContaining("users.csv")
                .hasMessageContaining("line 1")
                .hasMessageContaining("Invalid integer");
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

