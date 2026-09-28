package com.workflowpro.common.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.workflowpro.common.exception.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes 401/403 errors raised by the security filters (before any controller runs)
 * in the same JSON format as the GlobalExceptionHandler.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** No token, expired token or invalid signature. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        write(response, request, HttpStatus.UNAUTHORIZED, "Authentication is required or the access token is invalid");
    }

    /** Valid token, but the user's roles are not allowed here. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
                       String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        ErrorResponse body = ErrorResponse.of(status.value(), status.getReasonPhrase(), message,
                request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
