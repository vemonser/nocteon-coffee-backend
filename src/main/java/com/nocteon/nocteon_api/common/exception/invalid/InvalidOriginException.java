package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class InvalidOriginException extends BaseApiException {
    public InvalidOriginException() {
        super("error.origin.invalid", HttpStatus.BAD_REQUEST);
    }
}