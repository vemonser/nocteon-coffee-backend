package com.nocteon.nocteon_api.common.exception.product;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class CartEmptyException extends BaseApiException {
    public CartEmptyException() {
        super("error.cart.empty", HttpStatus.BAD_REQUEST);
    }
}
