package com.methodia.minibilling.controller;

import com.methodia.minibilling.controller.dto.ImportSummaryDto;
import com.methodia.minibilling.importer.CsvImportService;
import com.methodia.minibilling.importer.CsvImportSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final CsvImportService csvImportService;

    public ImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping
    public ResponseEntity<ImportSummaryDto> importCsvFiles() {
        CsvImportSummary summary = csvImportService.importAllFromInputDirectory();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ImportSummaryDto(summary.importedUsers(),
                                            summary.importedReadings(),
                                            summary.importedPrices(),
                                            summary.skippedDuplicates(),
                                            summary.errors()));
    }
}
