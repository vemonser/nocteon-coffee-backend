package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class InvalidCategoryException extends BaseApiException {
    public InvalidCategoryException() {
        super("error.category.invalid", HttpStatus.BAD_REQUEST);
    }
}