package com.nocteon.nocteon_api.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BaseApiException {

    public UnauthorizedException() {
        super("error.authentication.unauthorized", HttpStatus.UNAUTHORIZED);
    }

    public UnauthorizedException(String messageKey) {
        super(messageKey, HttpStatus.UNAUTHORIZED);
    }
}