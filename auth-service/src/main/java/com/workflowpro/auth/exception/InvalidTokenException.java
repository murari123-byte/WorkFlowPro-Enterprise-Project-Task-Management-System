package com.workflowpro.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends ApiException {

    public InvalidTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
    }
}
