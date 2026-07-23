package com.methodia.minibilling.controller;

import com.methodia.minibilling.exception.CsvRowParseException;
import com.methodia.minibilling.exception.GlobalExceptionHandler;
import com.methodia.minibilling.importer.CsvImportService;
import com.methodia.minibilling.importer.CsvImportSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportController.class)
@Import(GlobalExceptionHandler.class)
class ImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CsvImportService csvImportService;

    @Test
    void importEndpointReturnsSummary() throws Exception {
        when(csvImportService.importAllFromInputDirectory())
                .thenReturn(new CsvImportSummary(2, 3, 4, 1, List.of()));

        mockMvc.perform(post("/api/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedUsers").value(2))
                .andExpect(jsonPath("$.importedReadings").value(3))
                .andExpect(jsonPath("$.importedPrices").value(4))
                .andExpect(jsonPath("$.skippedDuplicates").value(1))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void importEndpointReturnsClearCsvError() throws Exception {
        when(csvImportService.importAllFromInputDirectory())
                .thenThrow(new CsvRowParseException("readings.csv", 2, "Invalid product: 'water'. Expected gas or elec"));

        mockMvc.perform(post("/api/import"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Invalid CSV input"))
                .andExpect(jsonPath("$.fileName").value("readings.csv"))
                .andExpect(jsonPath("$.lineNumber").value(2))
                .andExpect(jsonPath("$.reason").value("Invalid product: 'water'. Expected gas or elec"));
    }
}
