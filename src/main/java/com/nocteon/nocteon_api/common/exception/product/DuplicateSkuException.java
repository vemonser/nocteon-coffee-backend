package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class DuplicateSkuException extends BaseApiException {
    public DuplicateSkuException() {
        super("error.productVariant.duplicateSku", HttpStatus.CONFLICT);
    }
}