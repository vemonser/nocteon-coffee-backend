package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class ProductNotFoundException extends BaseApiException {
    public ProductNotFoundException() {
        super("error.product.notFound", HttpStatus.NOT_FOUND);
    }
}