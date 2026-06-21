package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class OriginNotFoundException extends BaseApiException {
    public OriginNotFoundException() {
        super("error.origin.notFound", HttpStatus.NOT_FOUND);
    }
}