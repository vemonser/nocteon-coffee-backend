package com.nocteon.nocteon_api.common.exception.notFound;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class OrderNotFoundException extends BaseApiException {
    public OrderNotFoundException() {
        super("error.order.notFound", HttpStatus.NOT_FOUND);
    }
}