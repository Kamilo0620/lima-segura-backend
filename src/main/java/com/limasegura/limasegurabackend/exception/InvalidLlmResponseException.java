package com.limasegura.limasegurabackend.exception;

import org.springframework.http.HttpStatus;

public class InvalidLlmResponseException extends ApiException {
    public InvalidLlmResponseException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
