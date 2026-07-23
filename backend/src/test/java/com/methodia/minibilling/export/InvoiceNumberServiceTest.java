package com.methodia.minibilling.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceNumberServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void startsFrom1000WhenNoInvoicesExist() {
        InvoiceNumberService service = new InvoiceNumberService(objectMapper());

        assertThat(service.reserveDocumentNumbers(tempDir, 1)).containsExactly("1000");
    }

    @Test
    void reservesFollowingNumbers() {
        InvoiceNumberService service = new InvoiceNumberService(objectMapper());

        assertThat(service.reserveDocumentNumbers(tempDir, 3)).containsExactly("1000", "1001", "1002");
        assertThat(service.reserveDocumentNumbers(tempDir, 2)).containsExactly("1003", "1004");
    }

    @Test
    void continuesAfterRestartFromHighestStoredDocumentNumber() throws IOException {
        writeInvoice("999");
        writeInvoice("1007");
        InvoiceNumberService serviceAfterRestart = new InvoiceNumberService(objectMapper());

        assertThat(serviceAfterRestart.reserveDocumentNumbers(tempDir, 2)).containsExactly("1008", "1009");
    }

    @Test
    void rescansOutputDirectoryBeforeReservingNumbers() throws IOException {
        InvoiceNumberService service = new InvoiceNumberService(objectMapper());
        assertThat(service.reserveDocumentNumbers(tempDir, 1)).containsExactly("1000");

        writeInvoice("1005");

        assertThat(service.reserveDocumentNumbers(tempDir, 1)).containsExactly("1006");
    }

    @Test
    void reservesUniqueNumbersWhenCalledInParallel() throws Exception {
        InvoiceNumberService service = new InvoiceNumberService(objectMapper());
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<String>) () -> service.reserveDocumentNumbers(tempDir, 1).getFirst())
                    .toList();

            Set<String> numbers = new HashSet<>();
            for (var future : executor.invokeAll(tasks)) {
                numbers.add(future.get());
            }

            assertThat(numbers).hasSize(20);
            assertThat(numbers).contains("1000", "1019");
        }
    }

    private void writeInvoice(String documentNumber) throws IOException {
        Path dir = tempDir.resolve("Consumer-REF");
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve(documentNumber + "-март-24.json"),
                """
                        {
                          "documentNumber": "%s"
                        }
                        """.formatted(documentNumber),
                StandardCharsets.UTF_8
        );
    }

    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
}
