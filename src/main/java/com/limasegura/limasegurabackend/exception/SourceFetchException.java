package com.limasegura.limasegurabackend.exception;

import org.springframework.http.HttpStatus;

public class SourceFetchException extends ApiException {
    public SourceFetchException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
    public SourceFetchException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY);
        initCause(cause);
    }
}
