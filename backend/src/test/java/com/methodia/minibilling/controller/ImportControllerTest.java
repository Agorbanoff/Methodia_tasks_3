package com.methodia.minibilling.controller;

import com.methodia.minibilling.config.SecurityConfig;
import com.methodia.minibilling.controller.dto.importing.FileImportResponse;
import com.methodia.minibilling.controller.dto.importing.FileImportResult;
import com.methodia.minibilling.controller.dto.importing.ImportValidationError;
import com.methodia.minibilling.importer.CsvImportService;
import com.methodia.minibilling.importer.FileImportUpload;
import com.methodia.minibilling.service.auth.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportController.class)
@Import(SecurityConfig.class)
class ImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CsvImportService csvImportService;

    @MockitoBean
    private MultipartImportRequestMapper multipartImportRequestMapper;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void allFilesSuccessfulReturnsOk() throws Exception {
        mockJwt("admin-token", "admin", "ADMIN");
        List<FileImportUpload> uploads = List.of(new FileImportUpload("CUSTOMERS",
                file("files[0].file", "customer_data.csv", "DUMMY-1001,Acme,T1\n")));
        when(multipartImportRequestMapper.toUploads(any(), any())).thenReturn(uploads);
        when(csvImportService.importFiles(uploads, "admin")).thenReturn(new FileImportResponse(List.of(
                successfulResult("USERS", "customer_data.csv")
        )));

        mockMvc.perform(multipart("/api/file/import")
                        .file(file("files[0].file", "customer_data.csv", "DUMMY-1001,Acme,T1\n"))
                        .param("files[0].type", "CUSTOMERS")
                        .param("uploadedBy", "ignored-client-value")
                        .cookie(cookie("admin-token")))
                .andExpect(status().isOk());

        verify(multipartImportRequestMapper).toUploads(any(), any());
        verify(csvImportService).importFiles(uploads, "admin");
    }

    @Test
    void oneInvalidFileReturnsBadRequestWithStructuredBody() throws Exception {
        mockJwt("admin-token", "admin", "ADMIN");
        List<FileImportUpload> uploads = List.of(new FileImportUpload("CUSTOMERS",
                file("files[0].file", "customer_data.csv", "bad\n")));
        when(multipartImportRequestMapper.toUploads(any(), any())).thenReturn(uploads);
        when(csvImportService.importFiles(uploads, "admin")).thenReturn(new FileImportResponse(List.of(
                failedResult("USERS", "customer_data.csv", "columns", "Expected 3 columns")
        )));

        mockMvc.perform(multipart("/api/file/import")
                        .file(file("files[0].file", "customer_data.csv", "bad\n"))
                        .param("files[0].type", "CUSTOMERS")
                        .cookie(cookie("admin-token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.results[0].success").value(false))
                .andExpect(jsonPath("$.results[0].errors[0].field").value("columns"));
    }

    @Test
    void mixedSuccessfulAndFailedResultsReturnBadRequest() throws Exception {
        mockJwt("admin-token", "admin", "ADMIN");
        List<FileImportUpload> uploads = List.of(
                new FileImportUpload("CUSTOMERS", file("files[0].file", "customer_data.csv", "DUMMY-1001,Acme,T1\n")),
                new FileImportUpload("READINGS", file("files[1].file", "usage_data.csv", "bad\n"))
        );
        when(multipartImportRequestMapper.toUploads(any(), any())).thenReturn(uploads);
        when(csvImportService.importFiles(uploads, "admin")).thenReturn(new FileImportResponse(List.of(
                successfulResult("USERS", "customer_data.csv"),
                failedResult("READINGS", "usage_data.csv", "columns", "Expected 4 columns")
        )));

        mockMvc.perform(multipart("/api/file/import")
                        .file(file("files[0].file", "customer_data.csv", "DUMMY-1001,Acme,T1\n"))
                        .file(file("files[1].file", "usage_data.csv", "bad\n"))
                        .param("files[0].type", "CUSTOMERS")
                        .param("files[1].type", "READINGS")
                        .cookie(cookie("admin-token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(jsonPath("$.results[1].success").value(false));
    }

    @Test
    void userReceivesForbidden() throws Exception {
        mockJwt("user-token", "user", "USER");

        mockMvc.perform(multipart("/api/file/import")
                        .file(file("files[0].file", "customer_data.csv", "DUMMY-1001,Acme,T1\n"))
                        .param("files[0].type", "CUSTOMERS")
                        .cookie(cookie("user-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestReceivesUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/file/import")
                        .file(file("files[0].file", "customer_data.csv", "DUMMY-1001,Acme,T1\n"))
                        .param("files[0].type", "CUSTOMERS"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceValidationErrorsReturnBadRequest() throws Exception {
        mockJwt("admin-token", "admin", "ADMIN");
        when(multipartImportRequestMapper.toUploads(any(), any()))
                .thenReturn(List.of(new FileImportUpload("BOGUS",
                        file("files[0].file", "customer_data.csv", "DUMMY-1001,Acme,T1\n"))));
        when(csvImportService.importFiles(any(), eq("admin")))
                .thenThrow(new IllegalArgumentException("Unsupported import type: BOGUS"));

        mockMvc.perform(multipart("/api/file/import")
                        .file(file("files[0].file", "customer_data.csv", "DUMMY-1001,Acme,T1\n"))
                        .param("files[0].type", "BOGUS")
                        .cookie(cookie("admin-token")))
                .andExpect(status().isBadRequest());
    }

    private MockMultipartFile file(String fieldName, String fileName, String content) {
        return new MockMultipartFile(fieldName, fileName, "text/csv", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private FileImportResult successfulResult(String type, String fileName) {
        return new FileImportResult(type, fileName, true, 1, List.of());
    }

    private FileImportResult failedResult(String type, String fileName, String field, String message) {
        return new FileImportResult(type, fileName, false, 0,
                List.of(new ImportValidationError(1, field, message)));
    }

    private Cookie cookie(String token) {
        return new Cookie(AuthController.AUTH_COOKIE_NAME, token);
    }

    private void mockJwt(String token, String username, String role) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(username);
        when(claims.get("role", String.class)).thenReturn(role);
        when(jwtService.parse(token)).thenReturn(claims);
    }
}
