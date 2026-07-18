package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class CartNotFoundException extends BaseApiException {
    public CartNotFoundException() {
        super("error.cart.notFound", HttpStatus.NOT_FOUND);
    }
}
