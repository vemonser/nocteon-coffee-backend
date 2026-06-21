package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class InvalidProductTypeException extends BaseApiException {
    public InvalidProductTypeException() {
        super("error.product.invalidType", HttpStatus.BAD_REQUEST);
    }
}