package com.workflowpro.task.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple endpoint to prove the service is up and reachable (directly or through the gateway).
 */
@RestController
@RequestMapping("/api/tasks")
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "task-service",
                "status", "UP",
                "timestamp", Instant.now().toString());
    }
}
