package com.workflowpro.common.exception;

import org.springframework.http.HttpStatus;

/** 422 - the input is well-formed but breaks a business rule (e.g. invalid status change). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
