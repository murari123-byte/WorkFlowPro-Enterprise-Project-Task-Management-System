package com.workflowpro.common.exception;

import org.springframework.http.HttpStatus;

/** 403 - the caller is logged in but this business rule does not allow the action. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
