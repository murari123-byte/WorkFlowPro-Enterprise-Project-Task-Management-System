package com.workflowpro.common.exception;

import org.springframework.http.HttpStatus;

/** 404 - the resource does not exist, or the caller may not know it exists. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
