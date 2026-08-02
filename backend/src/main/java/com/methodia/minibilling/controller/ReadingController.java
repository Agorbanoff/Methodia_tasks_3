package com.methodia.minibilling.controller;

import com.methodia.minibilling.controller.dto.common.PageResponse;
import com.methodia.minibilling.controller.dto.reading.ReadingResponse;
import com.methodia.minibilling.controller.dto.reading.SelfReportRequest;
import com.methodia.minibilling.controller.dto.reading.SelfReportResponse;
import com.methodia.minibilling.service.ReadingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/api/billing/readings")
public class ReadingController {

    private final ReadingService readingService;

    public ReadingController(ReadingService readingService) {
        this.readingService = readingService;
    }

    @PostMapping("/self-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public SelfReportResponse submitSelfReport(@Valid @RequestBody SelfReportRequest request,
                                               Authentication authentication) {
        return readingService.submitSelfReport(request, authentication.getName());
    }

    @GetMapping("/self-reports")
    public PageResponse<SelfReportResponse> listSelfReports(
            @RequestParam Optional<String> status,
            @RequestParam Optional<String> reference,
            @RequestParam Optional<String> service,
            Pageable pageable,
            Authentication authentication
    ) {
        return PageResponse.from(readingService.listSelfReports(status, reference, service, pageable,
                authentication.getName()));
    }

    @PostMapping("/self-reports/{id}/accept")
    public SelfReportResponse acceptSelfReport(@PathVariable String id, Authentication authentication) {
        return readingService.acceptSelfReport(id, authentication.getName());
    }

    @PostMapping("/self-reports/{id}/deny")
    public SelfReportResponse denySelfReport(@PathVariable String id, Authentication authentication) {
        return readingService.denySelfReport(id, authentication.getName());
    }

    @GetMapping
    public PageResponse<ReadingResponse> listReadings(
            @RequestParam Optional<String> reference,
            @RequestParam Optional<String> service,
            @RequestParam Optional<String> source,
            @RequestParam Optional<LocalDate> from,
            @RequestParam Optional<LocalDate> to,
            Pageable pageable,
            Authentication authentication
    ) {
        return PageResponse.from(readingService.listReadings(reference, service, source, from, to, pageable,
                authentication.getName()));
    }
}
