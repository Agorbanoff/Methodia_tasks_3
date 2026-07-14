package com.methodia.minibilling.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

@Service
public class InvoiceNumberService {

    private static final int FIRST_DOCUMENT_NUMBER = 1000;

    private final ObjectMapper objectMapper;
    private Integer nextDocumentNumber;

    public InvoiceNumberService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized List<String> reserveDocumentNumbers(Path outputDirectory, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Document number count cannot be negative");
        }
        int nextFromDisk = Math.max(FIRST_DOCUMENT_NUMBER, findHighestDocumentNumber(outputDirectory) + 1);
        nextDocumentNumber = nextDocumentNumber == null
                ? nextFromDisk
                : Math.max(nextDocumentNumber, nextFromDisk);

        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            numbers.add(String.valueOf(nextDocumentNumber));
            nextDocumentNumber++;
        }
        return numbers;
    }

    public int findHighestDocumentNumber(Path outputDirectory) {
        if (!Files.isDirectory(outputDirectory)) {
            return FIRST_DOCUMENT_NUMBER - 1;
        }

        try (Stream<Path> files = Files.walk(outputDirectory)) {
            OptionalInt highest = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .mapToInt(this::readDocumentNumberOrDefault)
                    .filter(number -> number >= FIRST_DOCUMENT_NUMBER)
                    .max();
            return highest.orElse(FIRST_DOCUMENT_NUMBER - 1);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan invoice output directory %s".formatted(outputDirectory), exception);
        }
    }

    private int readDocumentNumberOrDefault(Path file) {
        try {
            JsonNode root = objectMapper.readTree(file.toFile());
            JsonNode documentNumber = root.get("documentNumber");
            if (documentNumber == null) {
                return -1;
            }
            return Integer.parseInt(documentNumber.asText());
        } catch (IOException | NumberFormatException exception) {
            return -1;
        }
    }
}
