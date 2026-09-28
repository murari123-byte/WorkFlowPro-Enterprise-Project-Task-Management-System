package com.workflowpro.common.exception;

import org.springframework.http.HttpStatus;

/** 401 - the caller's token was rejected (e.g. it expired while one service was calling another). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
