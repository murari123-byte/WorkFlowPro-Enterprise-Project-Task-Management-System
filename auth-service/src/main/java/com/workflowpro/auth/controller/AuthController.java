package com.workflowpro.auth.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.workflowpro.auth.config.OpenApiConfig;
import com.workflowpro.auth.dto.AuthResponse;
import com.workflowpro.auth.dto.LoginRequest;
import com.workflowpro.auth.dto.RefreshTokenRequest;
import com.workflowpro.auth.dto.RegisterRequest;
import com.workflowpro.auth.dto.UserResponse;
import com.workflowpro.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new account (always gets the EMPLOYEE role) and log in")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Get a new access token using a refresh token (the refresh token is replaced)")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Log out: revokes the refresh token")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "The currently logged-in user", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        // The user id comes from the verified token, never from request parameters
        return authService.getCurrentUser(UUID.fromString(jwt.getSubject()));
    }
}
