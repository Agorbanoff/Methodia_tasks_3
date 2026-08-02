package com.methodia.minibilling.controller.dto.importing;

import java.util.List;

public record FileImportResult(
        String type,
        String fileName,
        boolean success,
        int importedRecords,
        List<ImportValidationError> errors
) {
}
