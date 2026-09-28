package com.workflowpro.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Settings every service needs to VERIFY access tokens ("app.jwt.*", from env vars).
 * The service refuses to start if the secret is missing or shorter than 32 characters.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        // HS256 needs a key of at least 256 bits = 32 bytes
        @NotBlank @Size(min = 32, message = "JWT_SECRET must be at least 32 characters") String secret,
        @NotBlank String issuer) {
}
