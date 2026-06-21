package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class InvalidRefreshTokenException extends BaseApiException {
    public InvalidRefreshTokenException() {
        super("error.refreshToken.invalid", HttpStatus.UNAUTHORIZED);
    }
}