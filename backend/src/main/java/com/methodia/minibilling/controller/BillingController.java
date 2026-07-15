package com.methodia.minibilling.controller;

import com.methodia.minibilling.controller.dto.HealthResponse;
import com.methodia.minibilling.service.BillingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return billingService.getHealth();
    }
}
