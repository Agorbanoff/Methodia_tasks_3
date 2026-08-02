package com.methodia.minibilling.controller;

import com.methodia.minibilling.controller.dto.importing.FileImportResponse;
import com.methodia.minibilling.importer.CsvImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
public class ImportController {

    private final CsvImportService csvImportService;
    private final MultipartImportRequestMapper multipartImportRequestMapper;

    public ImportController(CsvImportService csvImportService,
                            MultipartImportRequestMapper multipartImportRequestMapper) {
        this.csvImportService = csvImportService;
        this.multipartImportRequestMapper = multipartImportRequestMapper;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileImportResponse> importFiles(
            @RequestParam MultiValueMap<String, String> fields,
            @RequestParam MultiValueMap<String, MultipartFile> multipartFiles,
            Authentication authentication
    ) {
        FileImportResponse response = csvImportService.importFiles(
                multipartImportRequestMapper.toUploads(fields, multipartFiles),
                authentication.getName()
        );
        if (!response.allSuccessful()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
}
