package com.nocteon.nocteon_api.common.exception.user;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class PasswordMismatchException extends BaseApiException {
    public PasswordMismatchException() {
        super("error.password.mismatch", HttpStatus.BAD_REQUEST);
    }
}