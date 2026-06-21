package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class InvalidCredentialsException extends BaseApiException {
    public InvalidCredentialsException() {
        super("error.credentials.invalid", HttpStatus.UNAUTHORIZED);
    }
}