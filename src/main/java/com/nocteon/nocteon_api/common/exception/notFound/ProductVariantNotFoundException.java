package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class ProductVariantNotFoundException extends BaseApiException {
    public ProductVariantNotFoundException() {
        super("error.productVariant.notFound", HttpStatus.NOT_FOUND);
    }
}