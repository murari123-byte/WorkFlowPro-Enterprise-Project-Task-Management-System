package com.workflowpro.auth.dto;

/**
 * Returned by register, login and refresh.
 *
 * @param accessToken  short-lived JWT, sent as "Authorization: Bearer ..." on every API call
 * @param refreshToken long-lived, single-use token used only to get a new access token
 * @param expiresIn    access token lifetime in seconds
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {
}
