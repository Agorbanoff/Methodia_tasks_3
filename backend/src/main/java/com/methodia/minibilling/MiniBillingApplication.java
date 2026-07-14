package com.methodia.minibilling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniBillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniBillingApplication.class, args);
    }
}

