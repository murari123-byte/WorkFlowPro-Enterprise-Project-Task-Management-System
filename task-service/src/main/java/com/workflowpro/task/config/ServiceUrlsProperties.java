package com.workflowpro.task.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/** Where the other services run ("app.services.*" from env vars). */
@Validated
@ConfigurationProperties(prefix = "app.services")
public record ServiceUrlsProperties(@NotBlank String authUrl, @NotBlank String projectUrl) {
}
