package com.workflowpro.common.exception;

import org.springframework.http.HttpStatus;

/** 409 - the request clashes with the current state (duplicate, still in use, ...). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
