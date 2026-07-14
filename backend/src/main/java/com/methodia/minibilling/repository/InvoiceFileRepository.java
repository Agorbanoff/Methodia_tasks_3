package com.methodia.minibilling.repository;

import com.methodia.minibilling.config.BillingProperties;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;

@Repository
public class InvoiceFileRepository {

    private final BillingProperties billingProperties;

    public InvoiceFileRepository(BillingProperties billingProperties) {
        this.billingProperties = billingProperties;
    }

    public Path inputDirectory() {
        return Path.of(billingProperties.inputDirectory()).normalize();
    }

    public Path outputDirectory() {
        return Path.of(billingProperties.outputDirectory()).normalize();
    }

    public Path consumersFile() {
        return inputDirectory().resolve("users.csv").normalize();
    }

    public Path readingsFile() {
        return inputDirectory().resolve("readings.csv").normalize();
    }
}
