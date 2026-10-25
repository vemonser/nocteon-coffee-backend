package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class ValueExistsException extends BaseApiException {
    public ValueExistsException() {
        super("error.option.value.exists", HttpStatus.CONFLICT);
    }
}