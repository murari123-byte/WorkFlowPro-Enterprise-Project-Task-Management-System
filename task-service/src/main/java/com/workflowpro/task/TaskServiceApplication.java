package com.workflowpro.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Also scan the shared com.workflowpro.common package (exception handler, JWT verification)
@SpringBootApplication(scanBasePackages = {"com.workflowpro.task", "com.workflowpro.common"})
public class TaskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskServiceApplication.class, args);
    }
}
