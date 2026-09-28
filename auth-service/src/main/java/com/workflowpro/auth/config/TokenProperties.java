package com.workflowpro.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Token lifetimes, only needed by auth-service (the service that issues tokens).
 * Secret and issuer live in the shared {@link com.workflowpro.common.security.JwtProperties}.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record TokenProperties(
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {
}
