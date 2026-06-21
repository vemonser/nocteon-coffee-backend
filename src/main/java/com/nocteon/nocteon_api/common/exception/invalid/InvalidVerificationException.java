package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class InvalidVerificationException extends BaseApiException {
    public InvalidVerificationException() {
        super("error.verification.invalid", HttpStatus.BAD_REQUEST);
    }
}