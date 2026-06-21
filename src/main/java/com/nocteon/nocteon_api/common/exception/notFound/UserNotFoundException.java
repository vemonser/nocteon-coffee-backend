package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class UserNotFoundException extends BaseApiException {
    public UserNotFoundException() {
        super("error.user.notFound", HttpStatus.NOT_FOUND);
    }
}