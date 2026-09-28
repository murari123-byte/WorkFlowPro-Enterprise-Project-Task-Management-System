package com.workflowpro.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors we expect and want to show to the client with a specific HTTP status.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
