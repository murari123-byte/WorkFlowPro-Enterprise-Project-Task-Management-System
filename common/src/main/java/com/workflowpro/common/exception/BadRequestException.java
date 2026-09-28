package com.workflowpro.common.exception;

import org.springframework.http.HttpStatus;

/** 400 - the request itself is wrong (bad sort field, wrong current password, ...). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
