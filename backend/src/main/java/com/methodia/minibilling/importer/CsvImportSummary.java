package com.methodia.minibilling.importer;

import java.util.List;

public record CsvImportSummary(
        int importedUsers,
        int importedReadings,
        int importedPrices,
        int skippedDuplicates,
        List<String> errors
) {

    public CsvImportSummary add(CsvImportSummary other) {
        return new CsvImportSummary(
                importedUsers + other.importedUsers(),
                importedReadings + other.importedReadings(),
                importedPrices + other.importedPrices(),
                skippedDuplicates + other.skippedDuplicates(),
                combine(errors, other.errors())
        );
    }

    public static CsvImportSummary empty() {
        return new CsvImportSummary(0, 0, 0, 0, List.of());
    }

    private static List<String> combine(List<String> first, List<String> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        java.util.ArrayList<String> combined = new java.util.ArrayList<>(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }
}
