package com.workflowpro.auth.exception;

import org.springframework.http.HttpStatus;

import com.workflowpro.common.exception.ApiException;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
