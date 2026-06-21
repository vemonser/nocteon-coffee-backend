package com.nocteon.nocteon_api.common.exception.email;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class EmailAlreadyExistsException extends BaseApiException {
    public EmailAlreadyExistsException() {
        super("error.email.alreadyExists", HttpStatus.CONFLICT);
    }
}