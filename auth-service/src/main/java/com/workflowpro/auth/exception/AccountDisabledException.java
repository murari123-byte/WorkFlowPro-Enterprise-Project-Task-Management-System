package com.workflowpro.auth.exception;

import org.springframework.http.HttpStatus;

import com.workflowpro.common.exception.ApiException;

public class AccountDisabledException extends ApiException {

    public AccountDisabledException() {
        super(HttpStatus.FORBIDDEN, "This account is disabled");
    }
}
