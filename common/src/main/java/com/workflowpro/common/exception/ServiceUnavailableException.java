package com.workflowpro.common.exception;

import org.springframework.http.HttpStatus;

/** 503 - another service needed for this request could not be reached. */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
