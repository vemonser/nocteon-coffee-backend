package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class FarmNotFoundException extends BaseApiException {
    public FarmNotFoundException() {
        super("error.farm.notFound", HttpStatus.NOT_FOUND);
    }
}