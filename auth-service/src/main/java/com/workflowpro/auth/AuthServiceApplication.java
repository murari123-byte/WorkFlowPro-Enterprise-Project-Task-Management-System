package com.workflowpro.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Also scan the shared com.workflowpro.common package (exception handler, JWT verification)
@SpringBootApplication(scanBasePackages = {"com.workflowpro.auth", "com.workflowpro.common"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
