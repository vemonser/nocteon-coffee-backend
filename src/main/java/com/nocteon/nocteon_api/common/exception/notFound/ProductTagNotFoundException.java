package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class ProductTagNotFoundException extends BaseApiException {
    public ProductTagNotFoundException() {
        super("error.productTag.notFound", HttpStatus.NOT_FOUND);
    }
}
