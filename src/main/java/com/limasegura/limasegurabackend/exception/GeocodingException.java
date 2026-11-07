package com.limasegura.limasegurabackend.exception;

import org.springframework.http.HttpStatus;

public class GeocodingException extends ApiException {
    public GeocodingException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
