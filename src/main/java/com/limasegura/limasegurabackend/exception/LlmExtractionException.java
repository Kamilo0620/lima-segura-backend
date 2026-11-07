package com.limasegura.limasegurabackend.exception;

import org.springframework.http.HttpStatus;

public class LlmExtractionException extends ApiException {
    public LlmExtractionException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
    public LlmExtractionException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY);
        initCause(cause);
    }
}
