package com.nocteon.nocteon_api.common.exception.user;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class UsernameAlreadyExistsException extends BaseApiException {
    public UsernameAlreadyExistsException() {
        super("error.username.alreadyExists", HttpStatus.CONFLICT);
    }
}