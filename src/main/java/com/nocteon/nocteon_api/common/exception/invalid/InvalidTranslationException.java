package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class InvalidTranslationException extends BaseApiException {
    public InvalidTranslationException() {
        super("error.translation.invalid", HttpStatus.BAD_REQUEST);
    }
}