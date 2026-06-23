package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class CartItemNotFoundException extends BaseApiException {
    public CartItemNotFoundException() {
        super("error.cartItem.notFound", HttpStatus.NOT_FOUND);
    }
}