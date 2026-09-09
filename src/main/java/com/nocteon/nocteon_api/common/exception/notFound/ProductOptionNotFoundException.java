package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class ProductOptionNotFoundException extends BaseApiException {
    public ProductOptionNotFoundException() {
        super("error.product.optionNotFound", HttpStatus.NOT_FOUND);
    }
}