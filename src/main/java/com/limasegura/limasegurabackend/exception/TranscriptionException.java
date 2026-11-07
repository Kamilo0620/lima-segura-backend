package com.limasegura.limasegurabackend.exception;

import org.springframework.http.HttpStatus;

public class TranscriptionException extends ApiException {
    public TranscriptionException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
