package com.limasegura.limasegurabackend.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenActionException extends ApiException {
    public ForbiddenActionException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}