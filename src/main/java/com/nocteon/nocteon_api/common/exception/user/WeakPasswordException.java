package com.nocteon.nocteon_api.common.exception.user;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class WeakPasswordException extends BaseApiException {
    public WeakPasswordException() {
        super("error.password.weak", HttpStatus.BAD_REQUEST);
    }
}