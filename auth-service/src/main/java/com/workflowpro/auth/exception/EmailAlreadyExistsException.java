package com.workflowpro.auth.exception;

import org.springframework.http.HttpStatus;

import com.workflowpro.common.exception.ApiException;

public class EmailAlreadyExistsException extends ApiException {

    public EmailAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "An account with this email already exists");
    }
}
