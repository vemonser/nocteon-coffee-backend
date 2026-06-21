package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class CategoryNotFoundException extends BaseApiException {
    public CategoryNotFoundException() {
        super("error.category.notFound", HttpStatus.NOT_FOUND);
    }
}