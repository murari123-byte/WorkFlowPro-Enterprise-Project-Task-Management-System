package com.workflowpro.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Also scan the shared com.workflowpro.common package (exception handler, JWT verification)
@SpringBootApplication(scanBasePackages = {"com.workflowpro.project", "com.workflowpro.common"})
public class ProjectServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectServiceApplication.class, args);
    }
}
