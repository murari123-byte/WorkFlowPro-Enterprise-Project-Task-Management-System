package com.workflowpro.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for /refresh and /logout. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
