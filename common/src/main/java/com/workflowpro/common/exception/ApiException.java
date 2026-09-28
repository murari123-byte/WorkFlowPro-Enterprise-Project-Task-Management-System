package com.workflowpro.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected errors that should reach the client with a specific HTTP status.
 * Throw a subclass from the service layer; {@link GlobalExceptionHandler} turns it into JSON.
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
