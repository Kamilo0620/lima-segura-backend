package com.limasegura.limasegurabackend.exception;

import org.springframework.http.HttpStatus;

public class InvalidFileFormatException extends ApiException {
    public InvalidFileFormatException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}