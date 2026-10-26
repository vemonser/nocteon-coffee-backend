package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class InvalidCursorException extends BaseApiException {
    public InvalidCursorException() {
        super("error.cursor.invalid", HttpStatus.BAD_REQUEST);
    }
}
