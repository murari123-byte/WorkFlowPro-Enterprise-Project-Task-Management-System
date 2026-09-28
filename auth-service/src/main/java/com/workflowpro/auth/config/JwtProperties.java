package com.workflowpro.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JWT settings, bound from "app.jwt.*" in application.yml (values come from env vars).
 * The service refuses to start if the secret is missing or too short.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        // HS256 needs a key of at least 256 bits = 32 bytes
        @NotBlank @Size(min = 32, message = "JWT_SECRET must be at least 32 characters") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {
}
