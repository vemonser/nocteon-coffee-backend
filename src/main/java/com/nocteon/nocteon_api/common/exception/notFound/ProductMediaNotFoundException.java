package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class ProductMediaNotFoundException extends BaseApiException {
    public ProductMediaNotFoundException() {
        super("error.image.notFound", HttpStatus.NOT_FOUND);
    }

}
